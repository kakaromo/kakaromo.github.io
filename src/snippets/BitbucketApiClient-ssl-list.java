// @source src/main/java/com/samsung/move/bitbucket/service/BitbucketApiClient.java
// @lines 28-119
// @note HttpClient(SSL 무시) + listBranches 페이지네이션 + Bearer PAT + metadata authorTimestamp 추출
// @synced 2026-05-01T01:05:23.631Z


    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BitbucketApiClient() {
        // 자체 서명 인증서를 위한 SSL 무시 HttpClient
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());
            builder.sslContext(sslContext);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.warn("SSL 무시 설정 실패, 기본 SSL 사용: {}", e.getMessage());
        }
        this.httpClient = builder.build();
    }

    /**
     * 브랜치 정보: displayId(표시용), id(full ref), latestCommitId
     */
    public record BranchInfo(String name, String id, String latestCommitId, Long authorTimestamp) {}

    /**
     * Bitbucket Server REST API로 브랜치 목록 조회 (페이지네이션 처리)
     */
    public List<BranchInfo> listBranches(String serverUrl, String projectKey, String repoSlug, String pat)
            throws IOException, InterruptedException {

        List<BranchInfo> branches = new ArrayList<>();
        int start = 0;

        while (true) {
            String url = String.format("%s/rest/api/latest/projects/%s/repos/%s/branches?start=%d&limit=100",
                    serverUrl.replaceAll("/$", ""), projectKey, repoSlug, start);
            log.debug("[Bitbucket] 브랜치 조회 URL: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + pat)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("[Bitbucket] 브랜치 조회 응답: status={}, bodyLength={}", response.statusCode(), response.body().length());

            if (response.statusCode() != 200) {
                log.error("[Bitbucket] 브랜치 조회 실패: status={}, body={}", response.statusCode(), response.body());
                throw new IOException("Bitbucket API 응답 오류: " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.get("values");

            if (values != null && values.isArray()) {
                for (JsonNode branch : values) {
                    String displayId = branch.get("displayId").asText();
                    String branchId = branch.get("id").asText(); // full ref (refs/heads/...)
                    String commitId = branch.get("latestCommit").asText();
                    // metadata에서 최신 커밋 timestamp 추출
                    Long authorTimestamp = null;
                    JsonNode metadata = branch.get("metadata");
                    if (metadata != null) {
                        // Bitbucket Server의 branch-utils 플러그인 메타데이터
                        for (var it = metadata.fieldNames(); it.hasNext(); ) {
                            String key = it.next();
                            JsonNode meta = metadata.get(key);
                            if (meta != null && meta.has("authorTimestamp")) {
                                authorTimestamp = meta.get("authorTimestamp").asLong();
                                break;
                            }
                        }
                    }
                    branches.add(new BranchInfo(displayId, branchId, commitId, authorTimestamp));
                }
            }

            boolean isLastPage = root.has("isLastPage") && root.get("isLastPage").asBoolean(true);
            if (isLastPage) break;

            start = root.get("nextPageStart").asInt();
        }

        return branches;
    }
