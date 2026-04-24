// src/utils/downsample.rs — time-bucket 기반 다운샘플.
// 버킷마다 "첫 / 마지막 / qd 최댓값" 세 포인트만 남겨 trend + spike 보존.
// 전체 결과는 target_points × 3 이내. n ≤ target_points × 3 이면 원본 그대로.
pub fn time_bucket_decimate(
    batch: &RecordBatch,
    time_col_idx: usize,
    qd_col_idx: Option<usize>,
    target_points: usize,
) -> Result<RecordBatch, ArrowError> {
    let n = batch.num_rows();
    if n == 0 || target_points == 0 { return Ok(batch.clone()); }
    // 이미 충분히 작은 배치는 그대로 (샘플링이 오히려 왜곡)
    if n <= target_points.saturating_mul(3) { return Ok(batch.clone()); }

    let time_arr = batch.column(time_col_idx).as_any()
        .downcast_ref::<Float64Array>()
        .ok_or_else(|| ArrowError::SchemaError("time must be Float64".into()))?;
    let qd_arr = qd_col_idx.and_then(|idx|
        batch.column(idx).as_any().downcast_ref::<UInt32Array>().cloned());

    let t_min = time_arr.value(0);
    let t_max = time_arr.value(n - 1);
    if t_max <= t_min { return Ok(batch.clone()); }
    let dt = (t_max - t_min) / target_points as f64;
    if dt <= 0.0 { return Ok(batch.clone()); }

    let mut keepers: Vec<u32> = Vec::with_capacity(target_points * 3);
    let mut cur_bucket: i64 = -1;
    let mut bucket_first = 0u32;
    let mut bucket_last = 0u32;
    let mut bucket_argmax = 0u32;   // QD 스파이크 보존용
    let mut bucket_maxqd = 0u32;

    for i in 0..n {
        let t = time_arr.value(i);
        let mut b = ((t - t_min) / dt) as i64;
        if b < 0 { b = 0; }
        if b as usize >= target_points { b = (target_points - 1) as i64; }
        let qd_val = qd_arr.as_ref().map(|a| a.value(i)).unwrap_or(0);
        if b != cur_bucket {
            // 이전 버킷 flush — first/last/argmax 중복 제거해 push
            if cur_bucket >= 0 {
                keepers.push(bucket_first);
                if bucket_last != bucket_first { keepers.push(bucket_last); }
                if qd_arr.is_some() && bucket_argmax != bucket_first
                    && bucket_argmax != bucket_last { keepers.push(bucket_argmax); }
            }
            cur_bucket = b;
            bucket_first = i as u32; bucket_last = i as u32;
            bucket_argmax = i as u32; bucket_maxqd = qd_val;
        } else {
            bucket_last = i as u32;
            if qd_val > bucket_maxqd { bucket_maxqd = qd_val; bucket_argmax = i as u32; }
        }
    }
    // flush last bucket
    if cur_bucket >= 0 {
        keepers.push(bucket_first);
        if bucket_last != bucket_first { keepers.push(bucket_last); }
        if qd_arr.is_some() && bucket_argmax != bucket_first
            && bucket_argmax != bucket_last { keepers.push(bucket_argmax); }
    }

    keepers.sort_unstable();
    keepers.dedup();

    // take_record_batch 로 모든 컬럼에 동일 keeper set 적용
    let mut builder = UInt32Builder::with_capacity(keepers.len());
    for k in keepers { builder.append_value(k); }
    let indices = builder.finish();
    take_record_batch(batch, &indices)
}
