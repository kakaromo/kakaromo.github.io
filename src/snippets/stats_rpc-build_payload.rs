// src/output/stats_rpc.rs — 단일 RecordBatch 를 한 번만 스캔해 5~6가지 통계를 동시에 산출.
// 반복 순회 대신 loop 안에서 누적 배열/맵을 채워 O(n) 1회.
pub(crate) fn build_payload(
    batch: &RecordBatch,
    trace_type: &str,
    latency_ranges_ms: &[f64],
    schema_version: &'static str,
) -> Result<StatsPayload, Box<dyn std::error::Error + Send + Sync>> {
    let n = batch.num_rows() as u64;
    if n == 0 { return Ok(StatsPayload::empty(schema_version)); }

    // 모든 컬럼을 미리 downcast — 핫루프에서 as_any 반복 비용 회피
    let time   = col_f64(batch, "time")?;
    let action = col_str(batch, "action")?;
    let cmd    = col_str(batch, "cmd")?;
    let lba    = col_u64(batch, "lba")?;
    let size   = col_u32(batch, "size")?;
    let qd     = col_u32(batch, "qd")?;
    let dtoc   = col_f64(batch, "dtoc")?;
    let ctoc   = col_f64(batch, "ctoc")?;
    let ctod   = col_f64(batch, "ctod")?;
    // 구버전 parquet 호환: continuous 컬럼이 없으면 fallback 계산
    let continuous: Option<&BooleanArray> = batch.schema().index_of("continuous")
        .ok().and_then(|idx| batch.column(idx).as_any().downcast_ref::<BooleanArray>());

    // trace_type + 실제 action 리터럴 스캔으로 request/complete 결정
    // UFS: send_req / complete_rsp, Block ftrace: block_rq_issue / _complete, blktrace CSV: Q / C
    let (request_action, complete_action) = action_pair(trace_type, action);

    // duration — parquet 은 시간 정렬이지만 안전하게 min/max 재계산
    let mut tmin = f64::INFINITY; let mut tmax = f64::NEG_INFINITY;
    for i in 0..time.len() {
        let v = time.value(i);
        if v < tmin { tmin = v; } if v > tmax { tmax = v; }
    }
    let duration_seconds = if tmax > tmin { (tmax - tmin) / 1000.0 } else { 0.0 };

    // send_count + continuous/aligned 카운트 + I/O bytes — 한 루프에서 모두
    let use_request_basis = !request_action.is_empty();   // UFSCUSTOM 은 빈 문자열
    let target_action = if use_request_basis { request_action } else { complete_action };
    let mut send_count = 0u64;
    let mut continuous_count = 0u64;
    let mut aligned_count = 0u64;
    let mut prev_end_by_cmd: HashMap<String, u64> = HashMap::new();
    for i in 0..n as usize {
        if action.value(i) == request_action { send_count += 1; }
        if action.value(i) != target_action { continue; }
        let lba_v = lba.value(i); let size_v = size.value(i) as u64; let cmd_v = cmd.value(i);
        // continuous: parquet 컬럼 우선, 없으면 "이전 end == 현재 lba" fallback
        let is_cont = match continuous { Some(b) => b.value(i), None => {
            let prev = prev_end_by_cmd.get(cmd_v).copied().unwrap_or(u64::MAX);
            prev == lba_v
        }};
        if is_cont { continuous_count += 1; }
        if lba_v % 8 == 0 && size_v % 8 == 0 { aligned_count += 1; }
        prev_end_by_cmd.insert(cmd_v.to_string(), lba_v + size_v);
    }

    // percentile: 정렬 기반 linear interpolation (별도 lib 없음).
    //   detailed_latency_stats(&dtoc) → min/max/avg/stddev/median/p99/p999/p9999/p99999/p999999
    let dtoc_s = detailed_latency_stats(dtoc);
    let ctod_s = detailed_latency_stats(ctod);
    let ctoc_s = detailed_latency_stats(ctoc);
    let qd_s   = detailed_latency_stats_u32(qd);

    // cmd_stats / latency_histograms / cmd_size_counts 도 동일 loop 결과를 써서 O(n) 유지
    let cmd_stats = build_cmd_stats(cmd, action, lba, size, qd, dtoc, ctoc, ctod, request_action);
    let histograms = build_histograms(cmd, dtoc, ctoc, ctod, latency_ranges_ms);
    let cmd_size_counts = build_cmd_size(cmd, size);

    Ok(StatsPayload {
        total_events: n, send_count, duration_seconds,
        continuous_count, aligned_count,
        dtoc: dtoc_s, ctod: ctod_s, ctoc: ctoc_s, qd: qd_s,
        cmd_stats, latency_histograms: histograms, cmd_size_counts,
        schema_version, /* … read/write/discard bytes omit … */
        ..StatsPayload::default()
    })
}
