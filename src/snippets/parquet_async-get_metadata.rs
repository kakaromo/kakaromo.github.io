// src/output/parquet_async.rs — footer 2-step fetch.
//   Parquet footer layout: `... metadata-bytes | u32_le(metadata_len) | "PAR1" (4bytes) `
//   → 마지막 8 바이트에 metadata 길이가 있음. 최적 전략:
//     (1) tail 64KB 를 먼저 받아 길이 파싱 — 대부분 footer 가 64KB 내에 들어와 여기서 끝
//     (2) footer 가 64KB 초과면 필요한 앞쪽 범위 한 번 더 range-GET
fn get_metadata<'a>(
    &'a mut self,
    options: Option<&'a parquet::arrow::arrow_reader::ArrowReaderOptions>,
) -> BoxFuture<'a, ParquetResult<Arc<ParquetMetaData>>> {
    async move {
        let file_size = self.file_size;
        if file_size < 8 {
            return Err(ParquetError::General("parquet file too small".into()));
        }

        // (1) tail 최대 64KB 를 한 번에 fetch
        let initial = (64 * 1024u64).min(file_size);
        let initial_start = file_size - initial;
        let tail = self.get_bytes(initial_start..file_size).await?;

        // 마지막 8바이트 = [metadata_len(4) | "PAR1"(4)]
        let metadata_len = {
            let n = tail.len();
            if n < 8 {
                return Err(ParquetError::General("parquet footer truncated".into()));
            }
            let len_bytes: [u8; 4] = tail[n - 8..n - 4].try_into().unwrap();
            u32::from_le_bytes(len_bytes) as u64
        };
        let footer_total = metadata_len + 8;
        let need_extra = footer_total > initial;

        // (2) footer 가 64KB 를 넘으면 추가 fetch + concat
        let metadata_bytes: Bytes = if need_extra {
            let extra_start = file_size - footer_total;
            // 이미 받은 tail 과 안 겹치게 앞쪽만 받는다
            let extra = self.get_bytes(extra_start..file_size - initial).await?;
            let mut combined = Vec::with_capacity(footer_total as usize);
            combined.extend_from_slice(&extra);
            combined.extend_from_slice(&tail);
            Bytes::from(combined)
        } else {
            // tail 안에서 footer 부분만 슬라이스
            let from = (initial - footer_total) as usize;
            tail.slice(from..)
        };

        // (3) ParquetMetaDataReader 로 decode — page_index 등 옵션 반영
        let mut reader = ParquetMetaDataReader::new();
        if let Some(opts) = options {
            reader = reader.with_page_indexes(opts.page_index());
        }
        let metadata = reader.parse_and_finish(&metadata_bytes)
            .map_err(|e| ParquetError::General(format!("footer parse: {e}")))?;
        // 최종: ParquetMetaData — row group 위치·스키마·통계 정보.
        // 이후 stream 은 이 메타를 보고 필요한 chunk 만 range-GET.
        Ok(Arc::new(metadata))
    }.boxed()
}
