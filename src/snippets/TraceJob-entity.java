// com.samsung.move.trace.entity.TraceJob — portal_trace_jobs 테이블 매핑
@Entity
@Table(name = "portal_trace_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false, length = 64)
    private String userId;

    @Column(name = "originalFilename", nullable = false, length = 512)
    private String originalFilename;

    // MinIO 업로드 원본 — 재파싱 시 이 경로에서 다시 읽는다
    @Column(name = "uploadBucket", nullable = false, length = 64)
    private String uploadBucket;
    @Column(name = "uploadPath", nullable = false, length = 1024)
    private String uploadPath;

    // MinIO parquet 결과 — {prefix}ufs.parquet / block.parquet / ufscustom.parquet
    @Column(name = "parquetBucket", nullable = false, length = 64)
    private String parquetBucket;
    @Column(name = "parquetPrefix", nullable = false, length = 512)
    private String parquetPrefix;

    @Column(name = "sizeBytes")
    private Long sizeBytes;

    // UPLOADED → PARSING → PARSED | FAILED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TraceJobStatus status;

    @Column(name = "progressPercent")
    private Integer progressPercent;

    /** 현재 단계명 (DOWNLOADING / PARSING / CONVERTING / UPLOADING / COMPLETED / FAILED).
     *  Rust 의 ProcessLogsProgress.stage 를 받아 저장. UI 에 "어느 단계" 표시용. */
    @Column(name = "currentStage", length = 32)
    private String currentStage;

    @Column(name = "errorMessage", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "parsedAt")
    private LocalDateTime parsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
