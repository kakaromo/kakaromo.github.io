// src/output/chart_rpc.rs — parquet 파일을 ProjectionMask + RowFilter 로 읽는 헬퍼.
// 전체 파일을 스캔하지 않고 필요한 컬럼/행만 읽어내는 것이 핵심.
pub(crate) fn read_projected_parquet_with_time_col(
    parquet_path: &str,
    wanted_cols: &[&str],
    time_range: Option<(f64, f64)>,
    time_col_name: &str,
) -> Result<RecordBatch, Box<dyn std::error::Error + Send + Sync>> {
    let file = File::open(parquet_path)?;
    let builder = ParquetRecordBatchReaderBuilder::try_new(file)?;

    // (1) 컬럼 이름 → 인덱스 resolve. 모든 컬럼이 존재해야만 통과
    let parquet_schema = builder.parquet_schema();
    let resolved: Vec<usize> = wanted_cols.iter().filter_map(|name| {
        (0..parquet_schema.num_columns()).find(|&i| {
            parquet_schema.column(i).path().parts().last()
                .map(|p| p == *name).unwrap_or(false)
        })
    }).collect();
    if resolved.len() != wanted_cols.len() {
        return Err(format!("parquet schema missing cols {:?}", wanted_cols).into());
    }

    // (2) ProjectionMask — 이 leaf 컬럼들만 디코딩
    let projection = ProjectionMask::leaves(parquet_schema, resolved);
    let mut builder = builder.with_projection(projection).with_batch_size(65_536);

    // (3) time_range 주어지면 RowFilter 로 row group 수준에서 조기 pruning.
    //     ArrowPredicateFn: time 컬럼만 추가 투영해 predicate 평가 → BooleanArray 반환.
    if let Some((start_ms, end_ms)) = time_range {
        let schema = builder.schema().clone();
        let time_col_idx = schema.index_of(time_col_name)?;
        let time_proj = ProjectionMask::leaves(
            builder.parquet_schema(), std::iter::once(time_col_idx),
        );
        let predicate = ArrowPredicateFn::new(time_proj, move |batch: RecordBatch| {
            let col = batch.column(0).as_any()
                .downcast_ref::<Float64Array>()
                .ok_or_else(|| ArrowError::SchemaError("time must be Float64".into()))?;
            let lower = cmp::gt_eq(col, &Float64Array::new_scalar(start_ms))?;
            let upper = cmp::lt_eq(col, &Float64Array::new_scalar(end_ms))?;
            Ok(arrow::compute::and(&lower, &upper)?)
        });
        builder = builder.with_row_filter(RowFilter::new(vec![Box::new(predicate)]));
    }

    // (4) 실제 read — row group 단위 iterator 로 RecordBatch 스트림
    let projected_schema = builder.schema().clone();
    let reader = builder.build()?;
    let mut all_batches: Vec<RecordBatch> = Vec::new();
    for b in reader { all_batches.push(b?); }
    if all_batches.is_empty() {
        return Ok(RecordBatch::new_empty(projected_schema));
    }
    concat_batches(&projected_schema, &all_batches).map_err(Into::into)
}
