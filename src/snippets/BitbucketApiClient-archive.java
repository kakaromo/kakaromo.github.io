// @source src/main/java/com/samsung/move/bitbucket/service/BitbucketApiClient.java
// @lines 121-195
// @note downloadArchive (at=%2f encoded ref, InputStream) + getCommitTimestamp fallback
// @synced 2026-05-01T01:05:23.631Z

    /**
     * Bitbucket Server REST API로 ZIP 아카이브 다운로드.
     * branchName은 displayId (브랜치명)를 URL 인코딩하여 at 파라미터로 전달.
     */
    public InputStream downloadArchive(String serverUrl, String projectKey, String repoSlug,
                                       String branchId, String pat)
            throws IOException, InterruptedException {

        // branchId는 full ref (refs/heads/...), /를 %2f로 인코딩 (소문자, Bitbucket Server 호환)
        String encodedRef = branchId.replace("/", "%2f");

        String url = String.format("%s/rest/api/latest/projects/%s/repos/%s/archive?format=zip&at=%s",
                serverUrl.replaceAll("/$", ""), projectKey, repoSlug, encodedRef);
        log.debug("[Bitbucket] ZIP 다운로드 URL: {}", url);
        log.debug("[Bitbucket] branchId={}, encodedRef={}", branchId, encodedRef);

        // URI.create()가 %2f를 재인코딩하지 않도록 URI 직접 생성
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + pat)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        log.debug("[Bitbucket] 실제 요청 URI: {}", request.uri());
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        log.debug("[Bitbucket] ZIP 다운로드 응답: status={}, headers={}", response.statusCode(), response.headers().map());

        if (response.statusCode() != 200) {
            String body;
            try (InputStream is = response.body()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            log.error("[Bitbucket] ZIP 다운로드 실패: status={}, body={}", response.statusCode(), body);
            throw new IOException("ZIP 다운로드 실패: " + response.statusCode() + " - " + body);
        }

        return response.body();
    }

    /**
     * 브랜치의 최초 커밋 authorTimestamp 조회.
     * commits?until={branchId}&limit=1 로 조회.
     */
    public Long getCommitTimestamp(String serverUrl, String projectKey, String repoSlug,
                                   String branchId, String pat) {
        try {
            String encodedRef = branchId.replace("/", "%2f");
            String url = String.format("%s/rest/api/latest/projects/%s/repos/%s/commits?until=%s&limit=1",
                    serverUrl.replaceAll("/$", ""), projectKey, repoSlug, encodedRef);
            log.debug("[Bitbucket] 커밋 timestamp 조회 URL: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + pat)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode values = root.get("values");
                if (values != null && values.isArray() && !values.isEmpty()) {
                    JsonNode commit = values.get(0);
                    if (commit.has("authorTimestamp")) {
                        return commit.get("authorTimestamp").asLong();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Bitbucket] 커밋 timestamp 조회 실패: {}", e.getMessage());
        }
        return null;
    }
