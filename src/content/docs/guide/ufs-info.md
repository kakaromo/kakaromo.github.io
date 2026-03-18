---
title: UFS 참조 데이터
description: TR 생성 시 사용되는 7개 UFS 코드 테이블의 관리 방법
---

UFS 참조 데이터는 Test Request(TR) 생성 시 드롭다운 선택 항목으로 사용되는 코드 테이블입니다.

:::note
UFS Info 관리 기능은 **Admin 대시보드**의 **UFS Info** 탭으로 이동했습니다. Admin 로그인 후 접근할 수 있습니다.
:::

## 7개 코드 테이블

| 테이블 | 설명 | 예시 |
|--------|------|------|
| **CellType** | NAND 셀 타입 | SLC, MLC, TLC, QLC |
| **Controller** | UFS 컨트롤러 | 컨트롤러 모델명 |
| **Density** | 용량 | 128GB, 256GB, 512GB, 1TB |
| **NandSize** | NAND 공정 크기 | V7, V8, V9 |
| **NandType** | NAND 종류 | 3D NAND |
| **OEM** | 제조사 | 호스트 OEM 정보 |
| **UfsVersion** | UFS 규격 버전 | UFS 3.1, UFS 4.0 |

## CRUD 관리

Admin 대시보드 > UFS Info 탭에서 서브탭을 선택하여 각 코드 테이블의 CRUD를 수행할 수 있습니다.

- **생성**: 새 코드 항목 추가
- **조회**: 등록된 항목 목록 확인
- **수정**: 기존 항목의 이름 변경
- **삭제**: 더 이상 사용하지 않는 항목 제거

:::caution
이미 TR에서 참조 중인 코드 항목을 삭제하면 해당 TR의 표시에 영향을 줄 수 있습니다.
:::

## TR 생성 시 활용

TR 생성/수정 다이얼로그에서 UFS 관련 필드(Controller, Density, NandSize 등)는 드롭다운으로 표시됩니다. 이 드롭다운의 선택 항목이 바로 여기서 관리하는 참조 데이터입니다.

:::tip
새로운 UFS 모델이나 사양이 추가되면 먼저 Admin > UFS Info 탭에서 참조 데이터를 등록한 후 TR을 생성하세요.
:::
