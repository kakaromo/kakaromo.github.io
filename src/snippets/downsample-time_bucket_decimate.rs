// src/utils/downsample.rs — Stratified row sampling.
// 정확히 target_points row 보장 + outlier 시간 회복 + 보호 row 5종 + NaN/Inf 안전.
//
// 알고리즘 (n > target_points 일 때):
//   1) 시간축 target_points 등분, 각 bucket 의 첫 idx 하나만 후보 (NaN/Inf time 스킵)
//   2) min/max time idx 강제 포함 (차트 X축 범위 보장)
//   3) qd 컬럼 있으면 argmax(qd) idx 강제 포함 — QD 스파이크 보존
//   4) dtoc 컬럼 있으면 min/max dtoc idx 강제 포함 — latency 양극값 보존 (estrace 패턴)
//   5) 빈 bucket 으로 부족하면 row index 균등 sampling 으로 채움
//   6) 초과 시 보호 row 외에서 빽빽한 인접쌍 drop
//   7) 결과 idx 정렬 → take_record_batch
pub fn time_bucket_decimate_with_y(
    batch: &RecordBatch,
    time_col_idx: usize,
    qd_col_idx: Option<usize>,
    dtoc_col_idx: Option<usize>,
    target_points: usize,
) -> Result<RecordBatch, ArrowError> {
    let n = batch.num_rows();
    if n == 0 || target_points == 0 { return Ok(batch.clone()); }
    if n <= target_points { return Ok(batch.clone()); }  // 패스스루

    let time_arr = batch.column(time_col_idx).as_any()
        .downcast_ref::<Float64Array>()
        .ok_or_else(|| ArrowError::SchemaError("time must be Float64".into()))?;
    let qd_arr = qd_col_idx.and_then(|idx|
        batch.column(idx).as_any().downcast_ref::<UInt32Array>().cloned());
    let dtoc_arr = dtoc_col_idx.and_then(|idx|
        batch.column(idx).as_any().downcast_ref::<Float64Array>().cloned());

    // 1회 scan — t_min/t_max + (옵션) argmax(qd) + (옵션) min/max(dtoc)
    // NaN/Inf 인 row 는 후보에서 모두 스킵 (parquet 손상 / latency 미계산 안전).
    let mut t_min = f64::INFINITY; let mut t_max = f64::NEG_INFINITY;
    let mut idx_t_min: u32 = 0; let mut idx_t_max: u32 = 0;
    let mut idx_argmax_qd: Option<u32> = None; let mut max_qd: u32 = 0;
    let mut idx_min_dtoc: Option<u32> = None; let mut idx_max_dtoc: Option<u32> = None;
    let mut min_dtoc = f64::INFINITY; let mut max_dtoc = f64::NEG_INFINITY;
    for i in 0..n {
        let t = time_arr.value(i);
        if t.is_finite() {
            if t < t_min { t_min = t; idx_t_min = i as u32; }
            if t > t_max { t_max = t; idx_t_max = i as u32; }
        }
        if let Some(qd) = qd_arr.as_ref() {
            let v = qd.value(i);
            if idx_argmax_qd.is_none() || v > max_qd {
                idx_argmax_qd = Some(i as u32); max_qd = v;
            }
        }
        if let Some(dtoc) = dtoc_arr.as_ref() {
            let v = dtoc.value(i);
            if v.is_finite() {
                if v < min_dtoc { min_dtoc = v; idx_min_dtoc = Some(i as u32); }
                if v > max_dtoc { max_dtoc = v; idx_max_dtoc = Some(i as u32); }
            }
        }
    }
    if !t_min.is_finite() || !t_max.is_finite() || t_max <= t_min {
        return uniform_index_take(batch, n, target_points);
    }
    let dt = (t_max - t_min) / target_points as f64;
    if dt <= 0.0 { return uniform_index_take(batch, n, target_points); }

    // 1) 시간 균등 bucket — 각 bucket 의 첫 idx (NaN/Inf time 스킵)
    let mut keepers: Vec<u32> = Vec::with_capacity(target_points);
    let mut bucket_filled = vec![false; target_points];
    for i in 0..n {
        let t = time_arr.value(i);
        if !t.is_finite() { continue; }
        let mut b = ((t - t_min) / dt) as i64;
        if b < 0 { b = 0; }
        if b as usize >= target_points { b = (target_points - 1) as i64; }
        let bi = b as usize;
        if !bucket_filled[bi] {
            bucket_filled[bi] = true;
            keepers.push(i as u32);
        }
    }

    // 2) min/max time, 3) argmax(qd), 4) min/max(dtoc) 강제
    keepers.push(idx_t_min);
    keepers.push(idx_t_max);
    if let Some(idx) = idx_argmax_qd { keepers.push(idx); }
    if let Some(idx) = idx_min_dtoc { keepers.push(idx); }
    if let Some(idx) = idx_max_dtoc { keepers.push(idx); }
    keepers.sort_unstable();
    keepers.dedup();

    // 5) 빈 bucket 으로 부족하면 row index 균등 sampling 으로 채움.
    if keepers.len() < target_points {
        // ... step 균등 sampling, existing dedup ...
    }

    // 6) 초과 발생 시 가운데에서 drop — 보호 row 5종 절대 drop 안 함.
    let is_protected = |idx: u32| -> bool {
        idx == idx_t_min || idx == idx_t_max
            || idx_argmax_qd == Some(idx)
            || idx_min_dtoc == Some(idx) || idx_max_dtoc == Some(idx)
    };
    while keepers.len() > target_points {
        // 가장 인접 간격이 좁은 위치 찾고, 보호 대상 아닌 쪽 제거
        // ... drop 1 ...
    }

    // 7) RecordBatch take
    let mut builder = UInt32Builder::with_capacity(keepers.len());
    for k in keepers { builder.append_value(k); }
    let indices = builder.finish();
    take_record_batch(batch, &indices)
}
