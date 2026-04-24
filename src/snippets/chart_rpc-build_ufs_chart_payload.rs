// src/output/chart_rpc.rs — UFS parquet → Arrow IPC ChartPayload.
// 파이프라인: parquet projection+row-filter → 표준 10컬럼 batch → 통계 집계 → 다운샘플 → IPC 직렬화.
pub fn build_ufs_chart_payload(
    parquet_path: &str,
    time_range: Option<(f64, f64)>,
    target_points: u32,
    filter: Option<&FilterOptions>,
    actions: &[String],
) -> Result<ChartPayload, Box<dyn std::error::Error + Send + Sync>> {
    // 10컬럼만 투영 — I/O·메모리 절감. time_range 가 있으면 row-filter 로 조기 pruning.
    let columns = [
        "time", "action", "lba", "size", "qd",
        "dtoc", "ctoc", "ctod", "cpu", "opcode",
    ];
    let batch = read_projected_parquet(parquet_path, &columns, time_range)?;
    let total_events = batch.num_rows() as u64;

    // lba/qd/latency 범위 필터는 Arrow BooleanArray mask 조합으로 한 번에 적용
    let batch = apply_filters(&batch, filter, actions, /* lba_col */ Some("lba"))?;
    // 공용 스키마(build_chart_batch_ufs): opcode → cmd 리네임, 타입 고정
    let batch = build_chart_batch_ufs(&batch)?;

    // latency/qd 요약 통계 (chart header 의 X-Trace-Stats 에 Base64(JSON) 로 실림)
    let stats = compute_stats(&batch);

    // 시간 버킷 다운샘플: 버킷당 first/last/qd_argmax (최대 ×3)
    let decimated = time_bucket_decimate(
        &batch,
        idx(&batch, "time")?,
        Some(idx(&batch, "qd")?),
        target_points as usize,
    )?;
    let sampled_events = decimated.num_rows() as u64;

    // Arrow IPC StreamWriter → bytes. 응답 Content-Type: application/vnd.apache.arrow.stream
    let arrow_ipc = serialize_ipc(&decimated)?;

    Ok(ChartPayload {
        arrow_ipc,
        total_events,
        sampled_events,
        dtoc: stats.0, ctod: stats.1, ctoc: stats.2, qd: stats.3,
        schema_version: SCHEMA_VERSION_UFS,   // "ufs-v1" — 프론트 decoder 호환 체크
    })
}
