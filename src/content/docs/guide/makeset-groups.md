---
title: MakeSet 그룹
description: 다중 보드에 대한 MakeSet 설정을 그룹으로 저장하고 배치 적용하는 방법
---

MakeSet 그룹은 여러 종류의 보드에 대한 Provision/Image 설정을 미리 저장해두고, 다중 슬롯 선택 시 한 번의 조작으로 보드별 MakeSet을 배치 실행할 수 있는 기능입니다.

## MakeSet 그룹이란

기존에는 동일 product + board인 슬롯만 선택하여 MakeSet을 수행할 수 있었습니다. 여러 종류의 보드에 MakeSet을 하려면 보드별로 같은 작업을 반복해야 했습니다.

MakeSet 그룹을 사용하면:

- 보드별 Provision/Image 경로와 DD 값을 그룹으로 저장
- 여러 보드의 슬롯을 한꺼번에 선택해도 그룹 기반으로 자동 매칭
- 한 번의 Upload로 보드별 분리된 MakeSet 명령 일괄 전송

:::tip
FW 경로는 product(controller, nandType 등)에 따라 결정되므로 그룹에 저장하지 않습니다. FW는 다이얼로그에서 직접 지정하며 전체 슬롯에 공통 적용됩니다.
:::

## 그룹 생성

1. Slots 페이지에서 다중 슬롯을 선택하고 **MakeSet** 메뉴를 클릭합니다.
2. MakeSet 다이얼로그에서 **관리** 버튼을 클릭하여 그룹 관리 화면을 엽니다.
3. **새 그룹** 버튼을 클릭합니다.
4. 그룹 이름과 설명을 입력합니다.
5. 보드별 설정을 추가합니다:
   - **Board**: 보드 이름 (예: `SM-S928B`)
   - **Provision Path**: Provision XML 파일 경로
   - **Image Path**: Image 폴더 경로
   - **DD**: auto_dd 값 (기본: `none`)
6. **생성** 버튼을 클릭합니다.

:::tip
그룹 이름에 포함된 보드를 나열하면 구분하기 쉽습니다. 예: `SM-S928B + SM-A556E`
:::

## 그룹 사용 — 다중 슬롯 MakeSet

MakeSet 그룹의 핵심 사용처는 **Slots 페이지에서 다중 슬롯 선택 후 MakeSet**입니다.

1. 동일 product(controller, nandType, cellType, density가 같은)의 슬롯들을 선택합니다. 보드는 달라도 됩니다.
2. Context Menu 또는 Selection Sheet에서 **MakeSet**을 클릭합니다.
3. 다이얼로그에서 **MakeSet Group** 드롭다운으로 그룹을 선택합니다.
4. 선택한 슬롯들의 board가 그룹 items와 자동 매칭되어 보드별 설정 테이블이 표시됩니다.
5. FW가 필요하면 체크박스를 활성화하고 경로를 Browse합니다.
6. **Upload** 버튼을 클릭하면 보드별로 분리된 makeset 명령이 전송됩니다.

:::caution
그룹에 없는 보드의 슬롯은 자동으로 스킵됩니다. 스킵된 슬롯 수와 보드 이름이 경고로 표시됩니다.
:::

## 단일 슬롯 MakeSet

슬롯을 하나만 선택한 경우에는 기존과 동일한 UI가 표시됩니다. FW, Provision, Image를 각각 체크박스로 활성화하고 경로를 직접 Browse하여 설정합니다.

## 그룹 관리

### 수정

기존 그룹의 이름, 설명, 보드 설정을 변경할 수 있습니다. 그룹 관리 화면에서 편집 버튼을 클릭합니다.

### 삭제

더 이상 필요 없는 그룹을 삭제합니다.

:::caution
그룹 삭제는 되돌릴 수 없습니다. 삭제 전에 확인 다이얼로그가 표시됩니다.
:::

## FW와 Provision/Image의 분리

| 항목 | 범위 | 그룹 저장 | 이유 |
|------|------|-----------|------|
| **FW** | product별 | X | 잘못된 FW 적용 방지를 위해 수동 지정 |
| **Provision** | board별 | O | 보드마다 고정된 경로 |
| **Image** | board별 | O | 보드마다 고정된 경로 |
| **DD** | board별 | O | 보드별 auto_dd 설정 |
