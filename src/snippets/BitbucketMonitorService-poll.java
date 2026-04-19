// @source src/main/java/com/samsung/move/bitbucket/service/BitbucketMonitorService.java
// @lines 33-108
// @note @Scheduled fixedDelay 5분 polling + pollRepo + autoDownload 분기 (DETECTED 저장 or downloadBranch)
// @synced 2026-04-19T08:19:17.637Z

    /**
     * 5분 간격으로 활성화된 저장소의 새 브랜치 폴링
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void pollAllRepos() {
        if (!properties.isEnabled()) return;

        List<BitbucketRepo> repos = repoRepository.findByEnabledTrue();
        for (BitbucketRepo repo : repos) {
            try {
                pollRepo(repo);
            } catch (Exception e) {
                log.error("[Bitbucket] {} 폴링 실패: {}", repo.getName(), e.getMessage());
            }
        }
    }

    /**
     * 특정 저장소의 브랜치 목록을 조회하고 신규 브랜치를 다운로드
     */
    public int pollRepo(BitbucketRepo repo) {
        log.info("[Bitbucket] {} 폴링 시작", repo.getName());

        int downloaded = 0;
        try {
            List<BitbucketApiClient.BranchInfo> branches = apiClient.listBranches(
                    repo.getServerUrl(), repo.getProjectKey(), repo.getRepoSlug(), repo.getPat());
            log.debug("[Bitbucket] {} 브랜치 {}개 조회됨: {}", repo.getName(), branches.size(),
                    branches.stream().map(b -> b.name()).limit(10).toList());

            for (BitbucketApiClient.BranchInfo branch : branches) {
                boolean exists = branchRepository.existsByRepoIdAndBranchName(repo.getId(), branch.name());
                log.debug("[Bitbucket] 브랜치 '{}' — DB 존재={}", branch.name(), exists);
                if (!exists) {
                    if (repo.isAutoDownload()) {
                        downloadBranch(repo, branch.name(), branch.id(), branch.latestCommitId());
                        downloaded++;
                    } else {
                        // 감지만: DB에 DETECTED 상태로 기록
                        LocalDateTime commitDate = null;
                        if (branch.authorTimestamp() != null) {
                            commitDate = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(branch.authorTimestamp()),
                                    java.time.ZoneId.systemDefault());
                        } else {
                            Long ts = apiClient.getCommitTimestamp(
                                    repo.getServerUrl(), repo.getProjectKey(), repo.getRepoSlug(),
                                    branch.id(), repo.getPat());
                            if (ts != null) {
                                commitDate = LocalDateTime.ofInstant(
                                        java.time.Instant.ofEpochMilli(ts),
                                        java.time.ZoneId.systemDefault());
                            }
                        }
                        branchRepository.save(BitbucketBranch.builder()
                                .repoId(repo.getId())
                                .branchName(branch.name())
                                .latestCommitId(branch.latestCommitId())
                                .commitDate(commitDate)
                                .status("DETECTED")
                                .build());
                        log.info("[Bitbucket] 브랜치 '{}' 감지됨 (commitDate={})", branch.name(), commitDate);
                    }
                }
            }

            repo.setLastPolledAt(LocalDateTime.now());
            repoRepository.save(repo);
            log.info("[Bitbucket] {} 폴링 완료 — 브랜치 {}개 중 {}개 신규 다운로드",
                    repo.getName(), branches.size(), downloaded);
        } catch (Exception e) {
            log.error("[Bitbucket] {} 폴링 중 오류: {}", repo.getName(), e.getMessage());
        }

        return downloaded;
    }
