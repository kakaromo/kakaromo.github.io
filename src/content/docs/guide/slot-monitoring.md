---
title: 실시간 슬롯 모니터링
description: 슬롯 상태 실시간 확인, 다중 선택, Context Menu를 통한 테스트 제어 방법
---

Slots 페이지는 테스트 슬롯의 실시간 상태를 모니터링하고, 슬롯에 대한 다양한 제어 작업을 수행하는 핵심 페이지입니다.

## 슬롯 카드 UI

각 슬롯은 카드 형태로 표시되며, 현재 상태에 따라 색상이 달라집니다.

| 상태 | 색상 토큰 | 설명 |
|------|-----------|------|
| **pass** | emerald | 테스트 통과 |
| **warning_pass** | amber | 경고 포함 통과 |
| **warning** | amber | 경고 |
| **fail** | red | 테스트 실패 |
| **running** | emerald | 테스트 실행 중 (애니메이션) |
| **booting** | cyan | 부팅 중 (애니메이션) |
| **stop** | gray | 정지 |
| **disconnect** | slate | 연결 끊김 |

:::note
상태별 색상은 `$lib/config/slotState.ts`에서 중앙 관리됩니다. 새 상태 추가 시 해당 파일만 수정하면 SlotCard, ResultCell, 대시보드에 모두 반영됩니다.
:::

슬롯 카드에는 슬롯 번호, 현재 상태, 할당된 TR/TC 정보, 그리고 **product** 정보가 표시됩니다. product는 `Controller_NandType_CellType_NandSize_Density` 형식으로 조합된 계산 필드입니다.

### 슬롯 카드 툴팁

슬롯 카드에 마우스를 올리면 툴팁이 표시되어 해당 슬롯의 전체 상세 정보를 확인할 수 있습니다. 툴팁에는 modelName, battery, testState, setLocation, trName, runningState 등 HeadSlotData의 모든 주요 필드가 포함됩니다.

## 탭

페이지 상단에 Head 연결 목록 기반으로 탭이 동적 생성됩니다. 각 탭은 특정 Head에 연결된 슬롯 그룹을 표시합니다. 탭을 클릭하여 원하는 Head의 슬롯을 확인합니다.

## 슬롯 선택

다양한 방법으로 슬롯을 선택할 수 있습니다:

| 방법 | 동작 |
|------|------|
| **클릭** | 단일 슬롯 선택 (기존 선택 해제) |
| **Ctrl + 클릭** | 기존 선택 유지하며 추가/해제 |
| **Shift + 클릭** | 마지막 선택부터 현재까지 범위 선택 |
| **드래그** | 드래그 영역 내 슬롯 범위 선택 |
| **Ctrl + A** | 현재 탭의 모든 슬롯 선택 |

## Context Menu

슬롯을 우클릭하면 Context Menu가 표시됩니다.

| 메뉴 항목 | 설명 |
|-----------|------|
| **Terminal** | 해당 슬롯의 VM으로 원격 터미널 열기 |
| **Log Browser** | 슬롯 로그 디렉토리 브라우저 열기 |
| **Test Start** | 선택된 슬롯에서 테스트 시작 |
| **Test Stop** | 실행 중인 테스트 중지 |
| **Set TR** | TR 할당 |
| **Set TC** | TC 할당 |
| **Clear** | 슬롯의 TR/TC 할당 해제 |
| **Edit UFS** | UFS 정보 편집 |

:::caution
Test Stop은 실행 중인 테스트를 강제로 중지합니다. 진행 중인 데이터가 손실될 수 있으므로 주의하세요.
:::

## Selection Sheet

슬롯을 하나 이상 선택하면 화면 하단에 **Selection Sheet** 액션 패널이 나타납니다. Context Menu와 동일한 작업을 버튼으로 제공하며, 다중 선택된 슬롯에 일괄 작업을 수행할 수 있습니다. 각 슬롯 항목 옆에 Log Browser 버튼(폴더 아이콘)이 있어 해당 슬롯의 로그 디렉토리를 바로 열 수 있습니다.

## TC Group 빠른 적용

Set TC 작업 시 SetTC Sheet에서 저장된 TC 그룹을 Chip으로 빠르게 적용할 수 있습니다. 자세한 내용은 [TC 그룹](/guide/tc-groups/) 페이지를 참고하세요.

## RUNNING TC 실시간 성능 데이터

실행 중(RUNNING)인 슬롯 카드를 확장하면 현재 진행 중인 테스트의 **실시간 성능 데이터**를 확인할 수 있습니다. 테스트 완료를 기다리지 않고 진행 상황을 즉시 파악할 수 있습니다.

:::tip
슬롯 상태는 SSE(Server-Sent Events)를 통해 실시간으로 업데이트됩니다. 별도의 새로고침이 필요 없습니다.
:::
