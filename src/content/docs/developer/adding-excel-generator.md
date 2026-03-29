---
title: Excel Generator 추가하기
description: Go Excel Service에 새로운 Excel Generator를 추가하는 단계별 가이드
---

Go Excel Service(`excel-service`)에 새로운 Excel Generator를 추가하는 방법입니다. Generator는 성능 테스트 결과 JSON을 받아 네이티브 Excel 차트가 포함된 `.xlsx` 파일을 생성합니다.

## 전제 조건

- Go 1.21+ 설치
- `excel-service` 레포지토리 클론
- 해당 파서의 JSON 데이터 구조 파악 (`GET /api/performance-results/{historyId}/data`로 확인)

## 전체 흐름

```
1. generator/ 디렉토리에 Go 파일 생성
2. JSON 데이터 구조체 정의
3. ExcelGenerator 인터페이스 구현
4. registry에 parserId 매핑 등록
5. 빌드 & 테스트
```

---

## Step 1: Generator 파일 생성

`generator/` 디렉토리에 스네이크 케이스로 파일을 생성합니다.

```
generator/my_new_parser.go
```

---

## Step 2: JSON 데이터 구조체 정의

Spring 백엔드가 보내는 JSON 구조에 맞는 Go 구조체를 정의합니다. `json` 태그로 필드명을 매핑합니다.

```go
package generator

type myNewEntry struct {
    Cycle    int     `json:"cycle"`
    ReadMB   float64 `json:"readMB"`
    WriteMB  float64 `json:"writeMB"`
}
```

:::tip
실제 JSON 데이터는 `GET /api/performance-results/{historyId}/data` API로 확인할 수 있습니다. 브라우저 DevTools 네트워크 탭에서도 확인 가능합니다.
:::

---

## Step 3: ExcelGenerator 인터페이스 구현

`ExcelGenerator` 인터페이스는 메서드 하나만 구현하면 됩니다.

```go
type ExcelGenerator interface {
    Generate(data json.RawMessage, tcName, fw, setName string) (*excelize.File, string, error)
}
```

| 파라미터 | 설명 |
|----------|------|
| `data` | JSON 바이트 (Spring에서 전달) |
| `tcName` | 테스트 케이스 이름 |
| `fw` | 펌웨어 버전 |
| `setName` | 테스트 세트 이름 |

**반환값**: `excelize.File` (Excel 파일), `string` (파일명), `error`

---

## Step 4: 전체 코드 템플릿

아래는 실제 동작하는 최소 Generator 코드입니다. 이 패턴을 기반으로 확장하세요.

```go
package generator

import (
    "encoding/json"
    "fmt"

    "excel-service/styles"

    "github.com/xuri/excelize/v2"
)

// MyNewGenerator handles parser XX.
// Data: [ { "cycle": int, "readMB": float, "writeMB": float } ]
type MyNewGenerator struct{}

type myNewEntry struct {
    Cycle   int     `json:"cycle"`
    ReadMB  float64 `json:"readMB"`
    WriteMB float64 `json:"writeMB"`
}

func (g *MyNewGenerator) Generate(data json.RawMessage, tcName, fw, setName string) (*excelize.File, string, error) {
    // 1. JSON 파싱
    var entries []myNewEntry
    if err := json.Unmarshal(data, &entries); err != nil {
        return nil, "", fmt.Errorf("parse data: %w", err)
    }
    if len(entries) == 0 {
        return nil, "", fmt.Errorf("no data")
    }

    // 2. Excel 파일 생성
    f := excelize.NewFile()
    sheet := "MyNewParser"
    f.SetSheetName("Sheet1", sheet)

    // 3. 스타일 준비 (styles 패키지 사용)
    titleSty, _ := styles.TitleStyle(f)
    headerSty, _ := styles.HeaderStyle(f)
    dataSty, _ := styles.DataStyle(f)
    evenSty, _ := styles.EvenRowStyle(f)

    row := 1

    // 4. 타이틀 행
    f.SetCellValue(sheet, cellRef(0, row), "My New Parser - "+tcName)
    f.MergeCell(sheet, cellRef(0, row), cellRef(2, row))
    f.SetCellStyle(sheet, cellRef(0, row), cellRef(2, row), titleSty)
    row++

    // 5. 헤더 행
    headers := []string{"Cycle", "Read (MB/s)", "Write (MB/s)"}
    for i, h := range headers {
        f.SetCellValue(sheet, cellRef(i, row), h)
        f.SetCellStyle(sheet, cellRef(i, row), cellRef(i, row), headerSty)
    }
    row++

    // 6. 데이터 행
    for i, e := range entries {
        sty := dataSty
        if i%2 == 0 {
            sty = evenSty
        }
        f.SetCellValue(sheet, cellRef(0, row), e.Cycle)
        f.SetCellValue(sheet, cellRef(1, row), e.ReadMB)
        f.SetCellValue(sheet, cellRef(2, row), e.WriteMB)
        for col := 0; col < 3; col++ {
            f.SetCellStyle(sheet, cellRef(col, row), cellRef(col, row), sty)
        }
        row++
    }

    // 7. 컬럼 너비 설정
    f.SetColWidth(sheet, "A", "A", 10)
    f.SetColWidth(sheet, "B", "C", 15)

    // 8. 파일명 생성
    fileName := sanitizeFileName(tcName, fw) + ".xlsx"
    return f, fileName, nil
}
```

---

## Step 5: registry에 등록

`generator/generator.go`의 `registry` 맵에 parserId를 추가합니다.

```go
var registry = map[int64]ExcelGenerator{
    // ... 기존 매핑 ...
    XX: &MyNewGenerator{},  // XX = DB의 parserId
}
```

동일한 Generator가 여러 parserId를 처리할 수 있습니다:

```go
    30: &MyNewGenerator{},
    33: &MyNewGenerator{},
```

---

## Step 6: 빌드 및 확인

```bash
# 컴파일 에러 확인
go build ./...

# 정적 분석
go vet ./...

# 테스트 실행
go test ./...

# 로컬 실행 (포트 50052)
go run .
```

실행 후 Performance History 목록에서 다운로드 버튼으로 Excel 생성을 테스트합니다.

---

## 유틸리티 함수 레퍼런스

Generator 코드에서 사용할 수 있는 공통 함수들입니다.

### 셀 참조 함수 (`generator.go`)

| 함수 | 설명 | 예시 |
|------|------|------|
| `cellRef(col, row)` | 0-based 열, 1-based 행 → Excel 셀 주소 | `cellRef(0, 1)` → `"A1"` |
| `colName(idx)` | 0-based 열 인덱스 → 열 이름 | `colName(0)` → `"A"`, `colName(26)` → `"AA"` |
| `sanitizeFileName(tcName, fw)` | 파일명에 사용할 수 없는 문자 치환 | `"test/case"` → `"test_case"` |

### 스타일 함수 (`styles/styles.go`)

| 함수 | 용도 | 외관 |
|------|------|------|
| `TitleStyle(f)` | 섹션 타이틀 | 진한 회색 배경, 흰색 볼드, 중앙 정렬 |
| `HeaderStyle(f)` | 열/행 헤더 | 연한 파란색 배경, 볼드, 중앙 정렬 |
| `DataStyle(f)` | 일반 데이터 셀 | 우측 정렬, 얇은 테두리 |
| `EvenRowStyle(f)` | 짝수 행 (줄무늬) | 연한 회색 배경, 우측 정렬 |
| `LabelStyle(f)` | 좌측 정렬 라벨 | 볼드, 좌측 정렬 |
| `NumberFormat(f, fmt, even)` | 숫자 포맷 지정 | `"0.00"` 등 커스텀 포맷 |

모든 스타일에 얇은 회색(`CCCCCC`) 테두리가 기본 적용됩니다.

---

## 차트 추가하기

excelize의 `AddChart`를 사용하면 네이티브 Excel 차트를 삽입할 수 있습니다.

```go
// 라인 차트 예시
err := f.AddChart(sheet, cellRef(0, row), &excelize.Chart{
    Type: excelize.Line,
    Series: []excelize.ChartSeries{
        {
            Name:       "Read",
            Categories: fmt.Sprintf("%s!$A$3:$A$%d", sheet, dataEndRow),
            Values:     fmt.Sprintf("%s!$B$3:$B$%d", sheet, dataEndRow),
        },
        {
            Name:       "Write",
            Categories: fmt.Sprintf("%s!$A$3:$A$%d", sheet, dataEndRow),
            Values:     fmt.Sprintf("%s!$C$3:$C$%d", sheet, dataEndRow),
        },
    },
    Title: []excelize.RichTextRun{{Text: tcName}},
    PlotArea: excelize.ChartPlotArea{
        ShowVal: true,
    },
})
```

:::note
차트의 `Categories`와 `Values`는 `시트명!$열$시작행:$열$끝행` 형식의 절대 참조를 사용합니다. 데이터 영역의 시작/끝 행을 정확히 계산하세요.
:::

---

## 고급: 기존 Generator 재사용

### writeGenPerfSheet 공유 함수

`genperf.go`에 정의된 `writeGenPerfSheet()`는 범용 Read/Write 성능 데이터를 시트로 변환하는 공유 함수입니다. parserId 2, 3, 16 등이 이 함수를 사용합니다.

새 파서의 JSON 구조가 기존 GenPerf 형식과 동일하다면 `GenPerfGenerator`를 그대로 재사용할 수 있습니다:

```go
// generator.go의 registry에 매핑만 추가
XX: &GenPerfGenerator{},
```

### VluDirtyCase4 패턴

`dirty_case4.go`의 `VluDirtyCase4Generator`는 데이터 포맷을 자동 감지합니다. 유사한 구조의 파서는 같은 Generator를 공유할 수 있습니다 (예: parserId 25, 28).

---

## 체크리스트

새 Generator를 추가할 때 확인할 항목:

- [ ] `generator/` 디렉토리에 `.go` 파일 생성
- [ ] JSON 구조에 맞는 데이터 구조체 정의 (`json` 태그 확인)
- [ ] `ExcelGenerator` 인터페이스의 `Generate` 메서드 구현
- [ ] `generator/generator.go`의 `registry`에 parserId 매핑 추가
- [ ] 스타일은 `styles` 패키지 함수 사용 (직접 생성하지 않음)
- [ ] 셀 참조는 `cellRef(col, row)` 사용
- [ ] `go build ./...` 컴파일 성공
- [ ] `go vet ./...` 경고 없음
- [ ] 실제 데이터로 Excel 다운로드 테스트 완료
