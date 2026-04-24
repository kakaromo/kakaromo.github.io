// src/output/stats_rpc.rs — trace_type + action 리터럴 스캔으로 request/complete action 결정.
// Block 의 경우 ftrace 형식(block_rq_issue/_complete) 과 blktrace CSV 형식(Q/C) 둘 다 존재.
// UFS/UFSCUSTOM 은 형식이 고정돼 있어 스캔 없이 바로 리턴.
fn action_pair<'a>(trace_type: &str, action: &'a StringArray) -> (&'a str, &'a str) {
    // 앞 1024 행만 sniff — 실제 리터럴 분포를 파악
    let mut has_issue = false;
    let mut has_q = false;
    for i in 0..action.len().min(1024) {
        match action.value(i) {
            "block_rq_issue" => has_issue = true,
            "Q" => has_q = true,
            _ => {}
        }
    }

    if trace_type == "ufs" {
        return ("send_req", "complete_rsp");
    }
    if trace_type == "ufscustom" {
        // UFSCUSTOM 은 모든 이벤트가 완료 상태. request action 은 빈 문자열로 두어
        // send_count=0 유도 + continuous 계산은 complete 기준으로 폴백.
        return ("", "complete");
    }
    // Block 은 실제 리터럴에 따라 분기
    if has_issue { return ("block_rq_issue", "block_rq_complete"); }
    if has_q     { return ("Q", "C"); }
    // 기본값: ftrace 형태
    ("block_rq_issue", "block_rq_complete")
}
