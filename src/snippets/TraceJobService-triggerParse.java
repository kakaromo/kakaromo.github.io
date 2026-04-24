// com.samsung.move.trace.service.TraceJobService#triggerParse
// @Async 로 별도 스레드에서 수행: 업로드 API 는 이미 응답했고, 여기서부터 백그라운드.
// Rust 의 ProcessLogs streaming RPC 를 Iterator<ProcessLogsProgress> 로 받아 매 메시지마다 DB 업데이트.
@Async
public void triggerParse(Long jobId) {
    TraceJob job = jobRepo.findById(jobId).orElse(null);
    if (job == null) {
        log.warn("triggerParse: job {} not found", jobId);
        return;
    }
    try {
        job.setStatus(TraceJobStatus.PARSING);
        jobRepo.save(job);

        ProcessLogsRequest req = ProcessLogsRequest.newBuilder()
                .setSourceBucket(job.getUploadBucket())
                .setSourcePath(job.getUploadPath())
                .setTargetBucket(job.getParquetBucket())
                .setTargetPath(job.getParquetPrefix())
                .setLogType("auto")   // Rust 가 내용 sniff 로 UFS/Block/UFSCUSTOM 결정
                .build();

        // gRPC server-streaming: Rust 가 stage/progress 를 여러 번 push
        Iterator<ProcessLogsProgress> iter = traceGrpc.processLogs(req);
        ProcessLogsProgress last = null;
        while (iter.hasNext()) {
            ProcessLogsProgress p = iter.next();
            last = p;
            job.setProgressPercent(p.getProgressPercent());
            job.setCurrentStage(p.getStage().name());   // DOWNLOADING/PARSING/CONVERTING/UPLOADING/COMPLETED
            if (p.hasError()) {
                job.setErrorMessage(p.getError());
            }
            jobRepo.save(job);   // 매 메시지마다 DB 반영 → 프론트 2초 polling 이 실시간처럼 보임
        }

        if (last == null) { markFailed(job, "no progress message received"); return; }

        boolean success = last.hasSuccess() && last.getSuccess();
        if (!success) {
            markFailed(job, last.hasError() ? last.getError() : "parsing failed");
            return;
        }

        // output_files[] → TraceParquet rows. 같은 (jobId, traceType) 조합이면 upsert.
        for (String objectPath : last.getOutputFilesList()) {
            String traceType = detectTraceType(objectPath);   // *ufs.parquet / *block.parquet / *ufscustom.parquet
            if (traceType == null) continue;
            Optional<TraceParquet> existing =
                    parquetRepo.findByJobIdAndTraceType(job.getId(), traceType);
            TraceParquet tp = existing.orElseGet(() -> TraceParquet.builder()
                    .jobId(job.getId()).traceType(traceType).build());
            tp.setParquetPath(objectPath);
            parquetRepo.save(tp);
        }

        job.setStatus(TraceJobStatus.PARSED);
        job.setParsedAt(LocalDateTime.now());
        job.setProgressPercent(100);
        job.setCurrentStage("COMPLETED");
        jobRepo.save(job);
    } catch (Exception e) {
        log.error("triggerParse failed for job {}", jobId, e);
        markFailed(job, e.getMessage());
    }
}
