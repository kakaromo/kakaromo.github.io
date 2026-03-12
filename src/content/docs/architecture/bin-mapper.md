---
title: Binary Struct Mapper
description: C/C++ 구조체 파서 엔진(Lexer, Parser, BinaryReader)의 설계, 타입 시스템, 3가지 뷰 및 Predefined Structs DB 관리를 설명합니다.
---

## 개요

C/C++ 구조체 정의와 바이너리 파일을 입력받아, 바이너리 데이터를 구조체 필드에 매핑하여 시각화하는 개발 도구입니다. 펌웨어 디버깅, 바이너리 프로토콜 분석, 메모리 덤프 해석 등에 활용됩니다.

### 설계 참고 자료

외부 라이브러리를 사용하지 않고 직접 구현했으며, 아래 도구에서 영감을 받았습니다:

| 영역 | 참고 | 설명 |
|------|------|------|
| **Hex 뷰** | [ImHex](https://github.com/WerWolv/ImHex) | 필드별 색상 오버레이, Hex/ASCII 동기화, 바이트 클릭 → 필드 하이라이트 |
| **트리 테이블** | VS Code Variables 패널 | 재귀 트리 구조로 중첩 구조체/배열 표시 |
| **전체 레이아웃** | 010 Editor, HxD | 입력 → 결과 3탭(Table/Hex/JSON) 구조 |
| **C 파서** | 컴파일러 이론 (Lexer → Parser) | 교과서적 재귀하강 파서, C ABI 정렬 규칙 |
| **타입 시스템** | C11 표준 + Linux 커널 + Windows SDK | 158개 타입 alias 매핑 |

## 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                   Frontend (Svelte 5)                      │
│  /devtools/bin-mapper                                      │
│                                                            │
│  BinaryInput + StructInput + ControlBar                    │
│         |                                                  │
│         | POST /api/binmapper/parse                        │
│         v                                                  │
│  Result Viewer: TableView | HexStructView | JsonView       │
└─────────────────────────────────────────────────────────┘
                          |
                          v
┌─────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot)                     │
│                                                            │
│  BinMapperController --> BinMapperService                   │
│                              |                             │
│                    +---------+----------+                  │
│                    |                    |                   │
│              StructParserService  BinaryReaderService       │
│                    |                                       │
│              +-----+------+                                │
│              |            |                                │
│         CppStructLexer  CppStructParser                    │
└─────────────────────────────────────────────────────────┘
```

파싱은 3단계 파이프라인으로 진행됩니다:

1. **Lexer** (`CppStructLexer`): C/C++ 텍스트 → 토큰 리스트
2. **Parser** (`CppStructParser`): 토큰 리스트 → `StructDefinition`
3. **BinaryReader** (`BinaryReaderService`): `StructDefinition` + `byte[]` → `MappedResult`

---

## CppStructLexer: 토크나이저

Lexer는 C/C++ 소스 텍스트를 한 글자씩 읽으면서 의미 있는 토큰 단위로 쪼개는 역할을 합니다.

### 토큰 타입

| 카테고리 | 토큰 | 설명 |
|---------|------|------|
| 키워드 | `STRUCT`, `ENUM`, `TYPEDEF`, `UNION`, `CONST`, `SIGNED`, `UNSIGNED` | C 예약어 |
| 리터럴 | `IDENT`, `NUMBER` | 식별자, 숫자(10진수/16진수) |
| 구분자 | `LBRACE`/`RBRACE`, `LBRACKET`/`RBRACKET`, `LPAREN`/`RPAREN` | `{}`, `[]`, `()` |
| 구분자 | `SEMICOLON`, `COLON`, `COMMA`, `STAR`, `EQUALS` | `;`, `:`, `,`, `*`, `=` |
| 연산자 | `SHIFT_LEFT`, `PIPE`, `TILDE`, `MINUS` | `<<`, `\|`, `~`, `-` (enum 값 계산용) |
| 특수 | `ATTRIBUTE`, `PRAGMA_PACK`, `EOF` | GCC 속성, pragma, 파일 끝 |

### 토크나이징 과정

메인 루프에서 현재 글자를 확인하여 분기합니다:

- **공백/탭/개행**: 건너뜀
- **`//` 한줄 주석**: 줄 끝까지 건너뜀
- **`/* */` 블록 주석**: `*/` 나올 때까지 건너뜀
- **`#pragma pack`**: PRAGMA_PACK 토큰 생성 (`(push, 1)` 등)
- **`#if 0`**: 중첩 depth 추적하며 `#endif`까지 건너뜀
- **`__attribute__`**: 중첩 괄호 추적하여 ATTRIBUTE 토큰 생성
- **알파벳/`_`**: 식별자 읽기 → 키워드 맵 조회 → 키워드 또는 IDENT
- **숫자**: `0x` 접두사로 16진수 감지, `U`/`L`/`ULL` 접미사 자동 제거
- **`<<`**: SHIFT_LEFT 토큰
- **단일 문자**: `{`, `}`, `;` 등 대응하는 토큰 생성

---

## CppStructParser: 재귀하강 파서

### 파서 상태 (심볼 테이블)

```
typedefMap:       이미 파싱한 구조체 저장. 필드 타입 참조 시 사용
enumMap:          이미 파싱한 enum 저장. 필드 타입 참조 시 사용
pragmaPackValue:  현재 #pragma pack 값 (0=기본 정렬, 1=packed)
```

### parse() 메인 루프

토큰 리스트를 순회하며:

- **PRAGMA_PACK** → `pragmaPackValue` 갱신
- **TYPEDEF** → 다음 STRUCT/UNION/ENUM 파싱 후 typedefMap에 등록
- **STRUCT/UNION** → `parseStruct()` 호출
- **ENUM** → `parseEnum()` 호출
- **한정자/함수 선언** → 건너뜀

### parseStruct() -- 구조체 파싱

1. `__attribute__((packed))` 확인
2. 이름 읽기 (선택적)
3. packed 결정: `(pragmaPackValue == 1) || (__attribute__ packed)`
4. 필드 파싱 루프 (`}` 나올 때까지):
   - 중첩 STRUCT/UNION → 재귀 호출
   - ENUM → `parseEnum()` → enumMap 등록
   - 그 외 → `parseField()` (일반 필드)

### parseField() -- 필드 파싱

`const unsigned long long int *data[10][20] : 8 = 0;` 같은 복잡한 선언을 처리합니다:

1. 한정자 건너뛰기 (const, volatile, static 등)
2. 타입명 파싱 (`parseTypeName`) -- 여러 키워드 조합 지원
3. 포인터 `*` 건너뛰기
4. 필드 이름 읽기
5. 배열 크기 파싱 (다차원 → 1차원 평탄화)
6. 비트필드 확인 (`:` 뒤의 숫자)
7. 타입 해석 (4단계): CppType → enumMap → nestedStructs → typedefMap

### typedef, nested struct, pragma pack 지원

```c
// typedef 구조체
typedef struct { int32_t x; int32_t y; } Point;

// Packed 구조체
#pragma pack(push, 1)
struct TightStruct { uint8_t a; uint32_t b; };
#pragma pack(pop)

// Nested 구조체
struct Outer {
    uint32_t id;
    struct { uint16_t x; uint16_t y; } position;
};
```

---

## CppType: 158개 타입 alias

13개의 기본 타입으로 158개의 C/C++ 타입 이름을 매핑합니다.

| 기본 타입 | 크기 | 주요 alias |
|-----------|------|-----------|
| `INT8` | 1 | `int8_t`, `signed char`, `s8`, `__s8` |
| `UINT8` | 1 | `uint8_t`, `unsigned char`, `u8`, `BYTE`, `UCHAR` |
| `INT16` | 2 | `int16_t`, `short`, `s16`, `SHORT` |
| `UINT16` | 2 | `uint16_t`, `unsigned short`, `u16`, `WORD`, `__le16`, `__be16` |
| `INT32` | 4 | `int32_t`, `int`, `signed`, `s32`, `LONG` |
| `UINT32` | 4 | `uint32_t`, `unsigned int`, `u32`, `DWORD`, `UINT`, `__le32`, `__be32` |
| `INT64` | 8 | `int64_t`, `long long`, `s64`, `LONGLONG`, `ssize_t`, `intptr_t` |
| `UINT64` | 8 | `uint64_t`, `unsigned long long`, `u64`, `QWORD`, `SIZE_T`, `size_t`, `__le64` |
| `FLOAT` | 4 | `float`, `FLOAT` |
| `DOUBLE` | 8 | `double`, `DOUBLE`, `long double` |
| `CHAR` | 1 | `char`, `CHAR` |
| `BOOL` | 1 | `bool`, `_Bool`, `BOOL`, `BOOLEAN` |
| `BOOL32` | 4 | `BOOL32` |

타입 해석은 정적 HashMap에서 O(1) 조회합니다:

```java
CppType result = ALIAS_MAP.get(typeName.trim());
```

---

## BinaryReaderService

### 엔디언 자동 감지 (AUTO 모드)

바이너리 데이터 앞 5바이트를 확인하여:
- ELF 매직 넘버(`0x7F 'E' 'L' 'F'`) → byte[4]로 엔디언 결정
- 그 외 → Little Endian (기본값)

### 정렬/패딩 계산

C 컴파일러의 "자연 정렬" 규칙을 동일하게 구현합니다:

```
일반 구조체 (packed = false):
  struct Example {
      uint8_t  a;      // offset 0
      // 3바이트 패딩
      uint32_t b;      // offset 4 (4의 배수)
      uint16_t c;      // offset 8
      // 2바이트 패딩 (구조체 크기를 4의 배수로)
  };
  // 총 크기: 12바이트

Packed 구조체:
  struct __attribute__((packed)) Example {
      uint8_t  a;      // offset 0
      uint32_t b;      // offset 1 (패딩 없음!)
      uint16_t c;      // offset 5
  };
  // 총 크기: 7바이트
```

### 배열 반복 매핑

`repeatAsArray = true`이면 바이너리 크기를 구조체 크기로 나누어 여러 인스턴스를 매핑합니다:
- `instanceCount = data.length / structSize`

### 필드 매핑 처리

| 필드 종류 | 처리 |
|-----------|------|
| 비트필드 | storage unit 전체 읽기 → 비트 마스크 적용 |
| char 배열 | null-terminated 문자열로 변환 |
| 일반 배열 | 각 요소 개별 매핑 → children |
| 중첩 구조체 | 재귀적으로 mapFields() |
| 기본 타입 | ByteBuffer + CppType.readValue() |
| enum 타입 | 숫자 → 상수명 매핑 (예: `"BLUE (4)"`) |

---

## 3가지 뷰

### Table (재귀 트리)

Svelte 5 snippet으로 재귀 렌더링합니다. VS Code Variables 패널과 유사한 UI입니다.

- 들여쓰기 레벨 (depth)로 중첩 표현
- 확장/축소 버튼 (expandedPaths Set으로 상태 관리)
- 컬럼: Name, Type, Offset, Size, Hex, Value
- 컬럼 리사이즈 (드래그 핸들)
- 마우스 hover 시 HexStructView와 동기화 (`highlightedOffset`)

### Hex+Struct (색상 동기화)

ImHex 스타일의 Hex 뷰어로, 각 필드를 8색 팔레트로 색상 구분합니다.

**핵심 최적화:**
- **가상 스크롤링** (`@tanstack/svelte-virtual`): 1MB 바이너리 = 65,536행 중 화면에 보이는 ~30행만 DOM에 렌더링
- **O(1) 바이트→필드 룩업** (`Int16Array`): 바이너리 크기만큼의 정수 배열을 미리 만들어서 마우스 hover 시 즉시 필드 조회
- **키보드 탐색**: 화살표 키로 바이트 이동, Tab으로 필드 간 점프

```
바이트:      [0F] [00] [14] [00] [04] [00] [00] [00] [FF]
필드:        |  x (blue)|  y (green)|    color (yellow)  | a(pink)
byteFieldIdx: 0    0    1    1    2    2    2    2    3
```

### JSON

접기/펼기가 가능한 JSON 트리 뷰입니다. MappedResult를 JSON으로 표시합니다.

---

## Predefined Structs DB 관리

자주 사용하는 구조체를 DB에 저장하고 재사용할 수 있습니다.

### API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/binmapper/structs` | 전체 목록 (category → name 정렬) |
| `GET` | `/api/binmapper/structs/{id}` | 단건 조회 |
| `POST` | `/api/binmapper/structs` | 생성 |
| `PUT` | `/api/binmapper/structs/{id}` | 수정 |
| `DELETE` | `/api/binmapper/structs/{id}` | 삭제 |

### 테이블: `predefined_structs`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | PK |
| `name` | VARCHAR | 표시 이름 |
| `category` | VARCHAR | 분류 (그룹핑용) |
| `structText` | TEXT | C/C++ 구조체 원본 코드 |
| `description` | VARCHAR | 설명 (선택) |
| `createdAt` | DATETIME | 생성 시각 |
| `updatedAt` | DATETIME | 수정 시각 |

:::tip
StructInput 컴포넌트에서 카드 UI로 목록을 표시하고, [Select] 버튼으로 메인 에디터에 구조체 텍스트를 복사할 수 있습니다. .h 헤더 파일을 업로드하면 파일명에서 이름을 자동 추출합니다.
:::
