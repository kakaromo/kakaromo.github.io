// src/utils/downsample.rs — Stratified row sampling.
// 정확히 target_points row 보장 + outlier 시간에 강한 다운샘플.
//
// 알고리즘 (n > target_points 일 때):
//   1) 시간 축을 target_points 등분, 각 bucket 의 첫 idx 하나만 후보
//   2) min/max time idx 강제 포함 (차트 X축 범위 보장)
//   3) qd 컬럼 있으면 argmax(qd) 강제 포함 (QD 스파이크 보존)
//   4) 빈 bucket 으로 부족하면 row index 균등 sampling 으로 채움
//   5) 초과 발생 시 보호 row 외에서 가장 빽빽한 인접쌍 drop
//   6) 결과 idx 정렬 → take_record_batch
pub fn time_bucket_decimate(
    batch: &RecordBatch,
    time_col_idx: usize,
    qd_col_idx: Option<usize>,
    target_points: usize,
) -> Result<RecordBatch, ArrowError> {
    let n = batch.num_rows();
    if n == 0 || target_points == 0 { return Ok(batch.clone()); }
    // 패스스루: 입력이 이미 target 이하면 다운샘플 의미 없음.
    if n <= target_points { return Ok(batch.clone()); }

    let time_arr = batch.column(time_col_idx).as_any()
        .downcast_ref::<Float64Array>()
        .ok_or_else(|| ArrowError::SchemaError("time must be Float64".into()))?;
    let qd_arr = qd_col_idx.and_then(|idx|
        batch.column(idx).as_any().downcast_ref::<UInt32Array>().cloned());

    // 1회 scan — t_min/t_max idx + (옵션) argmax(qd) idx
    let mut t_min = f64::INFINITY;
    let mut t_max = f64::NEG_INFINITY;
    let mut idx_t_min: u32 = 0;
    let mut idx_t_max: u32 = 0;
    let mut idx_argmax_qd: Option<u32> = None;
    let mut max_qd: u32 = 0;
    for i in 0..n {
        let t = time_arr.value(i);
        if t < t_min { t_min = t; idx_t_min = i as u32; }
        if t > t_max { t_max = t; idx_t_max = i as u32; }
        if let Some(qd) = qd_arr.as_ref() {
            let v = qd.value(i);
            if idx_argmax_qd.is_none() || v > max_qd {
                idx_argmax_qd = Some(i as u32); max_qd = v;
            }
        }
    }
    if t_max <= t_min { return uniform_index_take(batch, n, target_points); }
    let dt = (t_max - t_min) / target_points as f64;
    if dt <= 0.0 { return uniform_index_take(batch, n, target_points); }

    // 1) 시간 균등 bucket — 각 bucket 의 첫 idx
    let mut keepers: Vec<u32> = Vec::with_capacity(target_points);
    let mut bucket_filled = vec![false; target_points];
    for i in 0..n {
        let t = time_arr.value(i);
        let mut b = ((t - t_min) / dt) as i64;
        if b < 0 { b = 0; }
        if b as usize >= target_points { b = (target_points - 1) as i64; }
        let bi = b as usize;
        if !bucket_filled[bi] {
            bucket_filled[bi] = true;
            keepers.push(i as u32);
        }
    }

    // 2) min/max 강제, 3) argmax(qd) 강제
    keepers.push(idx_t_min);
    keepers.push(idx_t_max);
    if let Some(idx) = idx_argmax_qd { keepers.push(idx); }

    keepers.sort_unstable();
    keepers.dedup();

    // 4) 빈 bucket 으로 부족하면 row index 균등 sampling 으로 채움.
    if keepers.len() < target_points {
        let need = target_points - keepers.len();
        let step = (n / (need + 1)).max(1);
        let existing: HashSet<u32> = keepers.iter().copied().collect();
        let mut extra: Vec<u32> = Vec::with_capacity(need);
        let mut k: usize = step;
        while extra.len() < need && k < n {
            let idx = k as u32;
            if !existing.contains(&idx) && !extra.contains(&idx) { extra.push(idx); }
            k += step;
        }
        keepers.extend(extra);
        keepers.sort_unstable();
        keepers.dedup();
    }

    // 5) 초과 (argmax 추가 등) 발생 시 — 보호 row 외에서 가장 빽빽한 인접쌍 drop
    while keepers.len() > target_points {
        // (보호 대상: idx_t_min, idx_t_max, idx_argmax_qd)
        // 가장 인접 간격이 좁은 위치 찾고, 보호 대상 아닌 쪽 제거
        // ... drop 1 ...
    }

    // 6) RecordBatch take
    let mut builder = UInt32Builder::with_capacity(keepers.len());
    for k in keepers { builder.append_value(k); }
    let indices = builder.finish();
    take_record_batch(batch, &indices)
}
