// src/storage/minio_client.rs — MinIO async 클라이언트의 range-GET 헬퍼.
// head_object 로 파일 크기 조회 + get_object_range 로 임의 byte range 다운로드.
// 두 함수가 있으면 parquet footer 2-step fetch + row group 단위 async read 가 성립.

/// MinIO object 의 크기(Content-Length) 조회. async streaming reader 용.
pub async fn head_object(
    &self,
    remote_path: &str,
) -> Result<u64, Box<dyn std::error::Error>> {
    let (head, _code) = self.bucket.head_object(remote_path).await?;
    let size = head.content_length
        .ok_or("head_object: missing Content-Length")?;
    if size < 0 {
        return Err("head_object: negative content-length".into());
    }
    Ok(size as u64)
}

/// MinIO object 의 byte range 다운로드.
/// NOTE: rust-s3 `get_object_range` 는 end **inclusive**.
///       parquet AsyncFileReader 는 `Range<u64>` (end exclusive) 를 넘기므로,
///       어댑터에서 `end = range.end - 1` 로 변환 후 호출.
pub async fn get_object_range(
    &self,
    remote_path: &str,
    start: u64,
    end: u64,
) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    let response = self.bucket
        .get_object_range(remote_path, start, Some(end))
        .await?;
    Ok(response.bytes().to_vec())
}
