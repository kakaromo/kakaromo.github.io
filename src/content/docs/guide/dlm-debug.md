---
title: DLM 디버그
description: 슬롯 디바이스에서 DLM(Device Log Memory) 바이너리를 추출하고, 파싱 및 분석하여 MinIO에 업로드하는 디버깅 워크플로우
---

DLM 디버그는 슬롯에 연결된 디바이스에서 DLM(Device Log Memory) 바이너리를 추출하고, BinMapper로 파싱하여 분석한 뒤, 필요 시 MinIO에 업로드하는 디버깅 기능입니다.

## 접근 방법

Slots 페이지에서 슬롯을 선택하고 우클릭 → **Debug > DLM** 메뉴를 클릭합니다.

:::note
Debug 서브메뉴는 DB의 `debug_types` 테이블에서 `enabled = true`인 타입 중, 프론트엔드 `debugRegistry`에 등록된 것만 표시됩니다. [관리자 대시보드](/guide/admin/#debug-management)에서 Debug Types/Tools를 관리할 수 있습니다.

DLM 메뉴는 다음 조건을 모두 만족해야 활성화됩니다:
- 선택된 슬롯이 모두 **연결 상태** (`connection === 1`)
- 테스트가 **실행 중이 아닌 상태** (`testState !== 'running'`)
- 모든 슬롯에 **USB ID(serial)**가 존재
:::

## 실행 흐름

DLM 다이얼로그는 3단계로 동작합니다:

### 1단계: Tool 선택 및 DLM 실행

1. **Tool** 드롭다운에서 사용할 DLM 바이너리를 선택합니다
2. **Execute** 버튼을 클릭하면 선택된 슬롯에 대해 순차적으로 DLM 추출이 진행됩니다

Tool 목록은 DB의 `debug_tools` 테이블에서 `type_key = 'dlm'`인 항목을 조회합니다. 관리자가 [Admin 페이지](/guide/admin/#debug-management)에서 등록합니다.

내부적으로 VM에 SSH 접속하여 다음 adb 명령을 순차 실행합니다:

1. **Push**: 선택된 Tool의 바이너리(`tool_path/tool_name`)를 디바이스에 전송
2. **Execute**: 디바이스에서 DLM 실행, stdout에서 출력 파일명 파싱
3. **Pull**: 결과 바이너리를 VM의 `/home/octo/tentacle/dlm/` 디렉토리로 가져옴

각 슬롯의 상태가 테이블에 실시간으로 표시됩니다:

| 상태 | 설명 |
|------|------|
| **pending** | 대기 중 |
| **running** | 실행 중 |
| **done** | 완료 |
| **error** | 오류 발생 |

출력 파일명은 `{tentacleName}{slotNum}-{testToolName}-{date}.bin` 형식입니다.

### 2단계: 바이너리 파싱

DLM 실행이 완료되면 결과 `.bin` 파일을 BinMapper로 파싱할 수 있습니다.

1. **Predefined Struct** 드롭다운에서 분석할 구조체 선택
2. **Parse** 버튼 클릭

기존 [BinMapper](/guide/bin-mapper/)의 서버 경로 파싱 기능을 재사용하여, VM에 있는 `.bin` 파일을 SFTP로 읽어 파싱합니다.

다중 슬롯인 경우 슬롯별 탭으로 결과를 전환할 수 있습니다.

### 3단계: 결과 확인 및 액션

파싱 결과는 3가지 뷰 모드로 확인할 수 있습니다:

| 뷰 모드 | 설명 |
|----------|------|
| **Table** | 필드별 계층 구조 테이블 |
| **Hex** | 바이너리 헥스 뷰 + 필드 매핑 |
| **JSON** | JSON 트리 뷰 |

이 뷰들은 BinMapper 페이지와 동일한 컴포넌트(`TableView`, `HexStructView`, `JsonView`)를 재사용합니다.

## 액션

파싱 결과 하단에서 다음 액션을 수행할 수 있습니다:

| 액션 | 설명 |
|------|------|
| **Download .bin** | 추출된 바이너리 파일을 브라우저로 다운로드 (SFTP 스트리밍) |
| **Upload to MinIO** | MinIO의 `dlm` 버킷에 파일 업로드 (버킷 자동 생성) |

:::tip
MinIO의 `dlm` 버킷이 없는 경우 첫 업로드 시 자동으로 생성됩니다. [파일 스토리지](/guide/file-storage/) 페이지에서 업로드된 DLM 파일을 관리할 수 있습니다.
:::

## 다중 슬롯 지원

여러 슬롯을 선택하여 DLM을 실행하면:

- 모든 슬롯에 대해 **순차적으로** DLM이 실행됩니다
- 각 슬롯의 진행 상태가 테이블에 실시간으로 표시됩니다
- 파싱 결과는 **슬롯별 탭**으로 전환하여 확인합니다
- Download/Upload 액션은 현재 활성 탭의 슬롯에 대해 동작합니다

## Debug Type/Tool 확장 구조

DLM은 Debug 시스템의 첫 번째 구현입니다. 새로운 디버그 타입을 추가하려면:

1. **DB**: `debug_types`에 새 타입 추가 (Admin 페이지에서)
2. **DB**: `debug_tools`에 해당 타입의 Tool 등록
3. **Frontend**: 전용 Dialog 컴포넌트 구현
4. **Frontend**: `debugRegistry.ts`에 `typeKey → { component, label }` 등록

등록된 타입은 Slots 페이지 Context Menu의 **Debug** 서브메뉴에 자동으로 표시됩니다.
