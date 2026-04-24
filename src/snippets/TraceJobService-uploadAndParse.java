// com.samsung.move.trace.service.TraceJobService#uploadAndParse
// 업로드 API 진입점. 주의: DB INSERT 를 MinIO PUT 보다 먼저 실행한다.
// 이유는 jobId 를 먼저 발급받아 MinIO object key 에 포함시키기 위함 (id 기반 고유성).
@Transactional
public TraceJob uploadAndParse(String userId, MultipartFile file) throws Exception {
    if (userId == null || userId.isBlank()) {
        throw new IllegalArgumentException("userId required");
    }
    String sanitizedUser = sanitizeId(userId);
    String filename = sanitizeFilename(file.getOriginalFilename());

    // 1) DB INSERT (status=UPLOADED) — jobId 선발급을 위해 MinIO 업로드보다 먼저.
    String today = LocalDate.now().toString();
    TraceJob job = TraceJob.builder()
            .userId(sanitizedUser)
            .originalFilename(filename)
            .uploadBucket(uploadsBucket)
            .uploadPath("placeholder")   // jobId 얻은 뒤 채움
            .parquetBucket(parquetBucket)
            .parquetPrefix("placeholder")
            .sizeBytes(file.getSize())
            .status(TraceJobStatus.UPLOADED)
            .progressPercent(0)
            .build();
    job = jobRepo.save(job);

    // 2) jobId 로 확정된 경로 채우고 re-save
    String uploadPath = String.format("%s/%s/%d/%s",
            sanitizedUser, today, job.getId(), filename);
    String parquetPrefix = String.format("%s/%d/", sanitizedUser, job.getId());
    job.setUploadPath(uploadPath);
    job.setParquetPrefix(parquetPrefix);
    jobRepo.save(job);

    // 3) MinIO PUT (원본 log 파일 저장)
    ensureBucket(uploadsBucket);
    ensureBucket(parquetBucket);
    storage.uploadObject(uploadsBucket, uploadPath,
            file.getInputStream(), file.getSize(), file.getContentType());

    // 4) 비동기 파싱 트리거 — 호출 직후 즉시 반환
    triggerParse(job.getId());
    return job;
}
