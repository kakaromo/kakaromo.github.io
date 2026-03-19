---
title: Excel Export
description: 프론트엔드(ExcelJS) 및 백엔드(Go gRPC) 두 가지 Excel Export 방식의 아키텍처와 구현을 설명합니다.
---

## 개요

성능 테스트 결과를 Excel 파일로 내보내는 두 가지 방식을 제공합니다.

| 방식 | 기술 | 용도 |
|------|------|------|
| **프론트엔드** | ExcelJS (dynamic import) | 화면에 보이는 차트 + 테이블을 서식 적용된 Excel로 내보내기 |
| **백엔드** | Go Excel Service (gRPC) | 서버사이드에서 네이티브 Excel 차트가 포함된 .xlsx 생성 |

---

## 프론트엔드 Excel Export (ExcelJS)

### 아키텍처

```
Perf Content Component (15개)
  |
  +-- chartRef.getImageDataURL()   # ECharts PNG 캡처
  +-- build sheets/sections
  +-- call exportToExcel()
        |
        v
excel-export.ts (공유 유틸리티)
  +-- dynamic import('exceljs')
  +-- image sections -> workbook.addImage()
  +-- table sections -> styled rows/cells
  +-- workbook.xlsx.writeBuffer()
  +-- Blob -> download
```

### 핵심 요구사항

- ECharts 차트를 PNG 이미지로 캡처하여 엑셀 시트에 삽입
- 테이블에 서식(헤더 색상, 테두리, 교번 행 색상) 적용
- 멀티탭 컴포넌트는 각 탭을 별도 시트로 생성
- 번들 최적화: 사용자가 Excel 버튼 클릭 시에만 ExcelJS 로드

### 컴포넌트별 패턴

| 패턴 | 컴포넌트 | 특징 |
|------|----------|------|
| A: 단일 차트 + 테이블 | WearLeveling, FragmentWrite, DirtyCase4Write 외 5개 | 1 chart + Statistics + Raw Data |
| B: 멀티탭 -> 멀티시트 | GenPerf, VluDirtyCase4, PerfByChunk | 활성 탭 캡처 / 비활성 탭 오프스크린 렌더링 |
| C: 테이블 전용 | KernelLatency, UnmapThroughput | 차트 이미지 없이 스타일된 테이블만 |
| D: 복수 차트 스택 | LongTermTC | Write + Read 차트를 한 시트에 세로 스택 |
| E: 차트 + 복합 테이블 -> 멀티시트 | VluLatency | Stats + Distribution + Percentile 시트 |

### 오프스크린 차트 렌더링

비활성 탭의 차트는 DOM에 마운트되어 있지 않으므로 `renderChartToImage()` 함수로 오프스크린 렌더링합니다:

```typescript
// 활성/비활성 모두 처리하는 패턴
const chartImage = chartRef?.getImageDataURL()
  ?? await renderChartToImage(chartOption);
```

:::tip
`renderChartToImage()`는 숨겨진 `<div>` 생성 → ECharts 인스턴스 초기화 → PNG 캡처(`pixelRatio: 2`) → 정리의 과정을 거칩니다. ECharts도 dynamic import로 로드됩니다. 현재 다크모드 상태를 감지하여 적절한 테마(`shine`/`shine-dark`)와 배경색을 자동 적용합니다.
:::

### 번들 최적화

ExcelJS와 ECharts 오프스크린 렌더링 모두 `dynamic import()`를 사용하여 초기 번들에 포함되지 않습니다:

```typescript
const { exportToExcel, renderChartToImage } = await import('$lib/utils/excel-export');
```

Vite 빌드 시 ExcelJS는 별도 청크로 분리됩니다.

---

## 백엔드 Excel Export (Go gRPC)

### Go Excel Service 아키텍처

별도 Go 서비스(`~/project/excel-service`)에서 네이티브 Excel 차트가 포함된 .xlsx를 생성합니다.

| 항목 | 설명 |
|------|------|
| 언어 | Go |
| 라이브러리 | excelize/v2 |
| 프로토콜 | gRPC (port 50052) |
| 패턴 | Strategy 패턴 (8개 Generator가 15개 파서 커버) |

### Proto 정의

`excel_service.proto`에서 서비스를 정의합니다:

```protobuf
service ExcelService {
    rpc GenerateExcel(ExcelRequest) returns (ExcelResponse);
}
```

Spring Boot와 Go 서비스가 동일한 proto 정의를 공유합니다.

### Spring 연동

| 클래스 | 역할 |
|--------|------|
| `ExcelGrpcClient` | `GrpcChannelFactory`로 stub 생성, Go 서비스 호출 |
| `ExcelExportController` | REST API 엔드포인트 |
| `PerformanceResultDataService` | 기존 controller 로직 추출, data/excel API 양쪽에서 재사용 |

### API

```
GET /api/performance-results/{historyId}/excel
```

1. `PerformanceResultDataService`에서 성능 데이터 조회
2. `ExcelGrpcClient`를 통해 Go 서비스에 gRPC 요청
3. Go 서비스가 excelize로 네이티브 Excel 차트 포함 .xlsx 생성
4. 바이너리 응답을 클라이언트에게 전달

### 채널 설정

```yaml
spring:
  grpc:
    client:
      channels:
        excel-service:
          address: static://localhost:50052
```

:::caution
실행 시 Go Excel 서비스가 50052 포트에서 먼저 실행되어 있어야 합니다.
:::
