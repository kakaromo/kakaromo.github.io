// src/output/parquet_async.rs — MinIO range-GET 기반 Parquet AsyncFileReader 어댑터.
// 기존 sync 경로는 `전체 다운로드 → /tmp → File::open`. 5GB 파일이면 /tmp I/O 10GB + RAM 피크 큼.
// 이 어댑터는 ParquetRecordBatchStreamBuilder(async) 에 그대로 꽂혀 row group 단위 async read 가능.

use bytes::Bytes;
use futures::future::BoxFuture;
use futures::FutureExt;
use parquet::arrow::async_reader::AsyncFileReader;
use parquet::errors::{ParquetError, Result as ParquetResult};
use parquet::file::metadata::{ParquetMetaData, ParquetMetaDataReader};
use std::ops::Range;
use std::sync::Arc;

use crate::storage::minio_client::{MinioAsyncClient, MinioConfig};

pub struct MinioParquetReader {
    client: Arc<MinioAsyncClient>,
    path: String,
    file_size: u64,
}

impl MinioParquetReader {
    pub async fn new(
        config: MinioConfig,
        path: String,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let client = MinioAsyncClient::new(&config)
            .map_err(|e| format!("minio client: {e}"))?;
        // head_object → Content-Length. footer 파싱 시 start 위치 계산에 필요.
        let size = client.head_object(&path).await
            .map_err(|e| format!("minio head_object {path}: {e}"))?;
        Ok(Self { client: Arc::new(client), path, file_size: size })
    }
}

impl AsyncFileReader for MinioParquetReader {
    // (A) 임의 byte range 공급 — parquet 크레이트가 row group/column chunk 요청할 때마다 호출.
    fn get_bytes(&mut self, range: Range<u64>) -> BoxFuture<'_, ParquetResult<Bytes>> {
        let client = Arc::clone(&self.client);
        let path = self.path.clone();
        async move {
            let start = range.start;
            // rust-s3 get_object_range 는 end inclusive — Range<u64>.end(exclusive) 와 규약이 다르므로 -1
            let end = range.end.saturating_sub(1);
            let bytes = client.get_object_range(&path, start, end).await
                .map_err(|e| ParquetError::General(format!("minio range-GET failed: {e}")))?;
            Ok(Bytes::from(bytes))
        }.boxed()
    }

    // (B) footer 2-step fetch 는 다음 스니펫에서 이어짐 …
    fn get_metadata<'a>(
        &'a mut self,
        options: Option<&'a parquet::arrow::arrow_reader::ArrowReaderOptions>,
    ) -> BoxFuture<'a, ParquetResult<Arc<ParquetMetaData>>> {
        async move { /* 다음 스니펫 참조 */ unimplemented!() }.boxed()
    }
}

/// 환경변수 `TRACE_PARQUET_READER=async` 로만 활성화 — 운영 배포 시 롤백 간편.
pub fn is_async_reader_enabled() -> bool {
    std::env::var("TRACE_PARQUET_READER").map(|v| v == "async").unwrap_or(false)
}
