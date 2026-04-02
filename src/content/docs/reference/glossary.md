---
title: 용어집
description: Samsung Portal에서 사용되는 주요 용어와 약어 정리
---

Samsung Portal에서 사용되는 주요 용어를 정리합니다.

| 용어 | 설명 |
|------|------|
| **TR (TestRequest)** | 테스트 요청 단위. 펌웨어 버전 등 테스트 대상 정보를 포함합니다. |
| **TC (TestCase)** | 테스트 케이스. 실행할 테스트의 종류와 설정을 정의합니다. |
| **History** | 테스트 실행 이력. TR + TC 조합의 실제 실행 결과를 기록합니다. |
| **Parser** | 성능 결과 데이터의 구조를 정의하는 파서. parserId로 시각화 컴포넌트와 매핑됩니다. |
| **Slot** | Head 서버의 테스트 슬롯. 디바이스가 물리적으로 연결되는 위치입니다. |
| **Head Server** | UFS 하드웨어 테스트를 제어하는 서버. TCP 듀얼 소켓으로 Portal과 통신합니다. |
| **Tentacle** | 테스트 디바이스가 연결된 서버. T1, T2, T3, T4로 구분됩니다. |
| **Set** | 테스트 대상 디바이스 세트. 슬롯 위치 정보를 포함합니다. |
| **SSE (Server-Sent Events)** | 서버에서 클라이언트로의 단방향 실시간 통신 프로토콜. 슬롯 상태 푸시에 사용됩니다. |
| **guacd** | Apache Guacamole 데몬. SSH/RDP 프로토콜을 Guacamole 프로토콜로 변환합니다. |
| **BinMapper** | Binary Struct Mapper. C/C++ 구조체 정의로 바이너리 데이터를 매핑하는 도구입니다. |
| **MinIO** | S3 호환 오브젝트 스토리지. 파일 업로드/다운로드/관리에 사용됩니다. |
| **UFS (Universal Flash Storage)** | 모바일 및 임베디드 시스템용 플래시 스토리지 표준입니다. |
| **NAND** | 비휘발성 메모리. UFS 스토리지의 물리적 저장 매체입니다. |
| **FW (Firmware)** | 펌웨어. UFS 디바이스의 내장 소프트웨어입니다. |
| **OEM** | 주문자 상표 부착 생산 (Original Equipment Manufacturer). UFS 제조사를 의미합니다. |
| **TC Group** | 자주 사용하는 TC 조합을 저장한 그룹. Slots 페이지에서 빠르게 적용할 수 있습니다. |
| **gRPC** | Google이 개발한 원격 프로시저 호출 프레임워크. Excel Service와의 통신에 사용됩니다. |
| **DataZoom** | ECharts의 줌 기능. 마우스 휠로 차트를 확대/축소합니다. |
| **HikariCP** | Spring Boot 기본 JDBC 커넥션 풀입니다. |
| **Runes** | Svelte 5의 반응성 시스템. `$state`, `$derived`, `$effect`, `$props`를 포함합니다. |
| **Reparse** | 성능 결과 재파싱. 완료된 테스트의 원본 로그에서 결과 JSON을 다시 생성하는 기능입니다. |
| **parsingcontroller** | 원격 tentacle 서버에 배포된 로그 파싱 바이너리. `parsingMethod`와 로그 파일을 입력받아 결과 JSON을 생성합니다. |
| **headType** | HEAD 연결 타입을 구분하는 정수 값. 0=호환성 테스트, 1=성능 테스트. Slots 페이지에서 탭 필터링에 사용됩니다. |
| **SessionLock** | VM 세션 잠금. 원격 접속 시 다른 사용자의 동시 접근을 방지하는 배타적 잠금 메커니즘입니다. |
| **AppMacro** | 앱 이벤트 녹화/재생 기능. Android 디바이스에서 사용자 인터랙션을 기록하고 자동 재생합니다. |
| **ScenarioCanvas** | @xyflow/svelte 기반 시나리오 편집기. 노드와 엣지로 Agent 시나리오를 시각적으로 구성합니다. |
| **DAG (Directed Acyclic Graph)** | 방향 비순환 그래프. ScenarioCanvas에서 조건 분기가 포함된 시나리오의 실행 흐름을 표현합니다. |
| **latencyType** | ioType 비트마스크. R(Read)=1, W(Write)=2, U(Unknown)=4. 조합 예: RW=3, RWU=7. `parsingcontroller`에 전달됩니다. |
