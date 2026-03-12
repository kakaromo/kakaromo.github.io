---
title: BinMapper API
description: C/C++ 구조체와 바이너리 데이터를 매핑하는 BinMapper 도구의 파싱 및 Predefined Struct API
---

BinMapper API는 C/C++ 구조체 정의와 바이너리 파일을 매핑하여 구조화된 데이터로 변환하는 기능을 제공합니다.

## 바이너리 파싱

### POST `/api/binmapper/parse`

바이너리 데이터를 C/C++ 구조체로 매핑합니다. `multipart/form-data` 형식입니다.

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `binaryFile` | file | 둘 중 하나 | 바이너리 파일 업로드 |
| `serverPath` | string | 둘 중 하나 | 서버 파일시스템 경로 |
| `structText` | string | 셋 중 하나 | C/C++ 구조체 텍스트 |
| `structFile` | file | 셋 중 하나 | 헤더 파일(.h) 업로드 |
| `predefinedStructId` | number | 셋 중 하나 | DB 저장 구조체 ID |
| `structName` | string | 선택 | 사용할 구조체 이름 (헤더에 여러 개 있을 때) |
| `endianness` | string | 선택 | `AUTO` (기본), `LITTLE`, `BIG` |
| `repeatAsArray` | boolean | 선택 | `false` (기본). 구조체 배열 반복 매핑 |

**응답 (`MappedResult`):**

```json
{
  "structName": "MyStruct",
  "structSize": 16,
  "totalBytes": 64,
  "endianness": "LITTLE",
  "instanceCount": 4,
  "instances": [
    {
      "index": 0,
      "offset": 0,
      "fields": [
        {
          "name": "id",
          "type": "uint32_t",
          "offset": 0,
          "size": 4,
          "value": "42",
          "hexBytes": "2A 00 00 00"
        }
      ]
    }
  ],
  "hexDump": "00000000  2A 00 00 00 ..."
}
```

### POST `/api/binmapper/parse-struct`

구조체 텍스트만 파싱하여 구조를 확인합니다. 바이너리 파일 없이 구조체 정의가 올바른지 검증할 때 사용합니다.

### POST `/api/binmapper/parse-header`

헤더 파일(.h)에서 구조체 목록을 추출합니다.

---

## Predefined Structs

자주 사용하는 구조체를 DB에 저장하고 관리합니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/binmapper/structs` | 전체 목록 (category, name 정렬) |
| GET | `/api/binmapper/structs/{id}` | 단건 조회 |
| POST | `/api/binmapper/structs` | 생성 |
| PUT | `/api/binmapper/structs/{id}` | 수정 |
| DELETE | `/api/binmapper/structs/{id}` | 삭제 |

**요청 (POST/PUT):**

```json
{
  "name": "UFS Descriptor",
  "category": "UFS",
  "structText": "typedef struct {\n    uint32_t id;\n    uint16_t flags;\n    uint8_t reserved[10];\n} UfsDescriptor;"
}
```

**응답 (GET):**

```json
{
  "id": 1,
  "name": "UFS Descriptor",
  "category": "UFS",
  "structText": "typedef struct { ... } UfsDescriptor;",
  "createdAt": "2026-03-05T10:00:00",
  "updatedAt": "2026-03-05T10:00:00"
}
```

## 지원 기능

- **타입**: 12개 기본 타입 + 90개 alias (uint32_t, int16_t, BOOL8, BOOL32 등)
- **구조체**: typedef, nested struct, pragma pack, attribute packed
- **정렬/패딩**: 자동 계산
- **Endianness**: Auto-detect, Little Endian, Big Endian
- **배열 반복**: `repeatAsArray=true` 시 파일 크기만큼 구조체 반복 매핑
- **ASCII 힌트**: 정수 필드의 각 바이트가 인쇄 가능한 ASCII면 자동 표시 (예: `573785173 ('"3DU')`)
