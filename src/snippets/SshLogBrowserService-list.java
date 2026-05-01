// @source src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java
// @lines 59-97
// @note SSH listFiles — ChannelSftp.ls + 폴더 먼저 정렬
// @synced 2026-05-01T01:05:23.626Z

    @Override
    public List<FileEntry> listFiles(String tentacleName, String path) {
        String remotePath = resolvePath(path);
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = createSessionByTentacle(tentacleName);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(30000);

            channel.cd(remotePath);

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(".");

            List<FileEntry> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name)) continue;
                SftpATTRS attrs = entry.getAttrs();
                result.add(new FileEntry(
                        name,
                        attrs.isDir(),
                        attrs.getSize(),
                        attrs.getMTime() * 1000L
                ));
            }
            result.sort(Comparator
                    .comparing((FileEntry f) -> !f.directory())
                    .thenComparing(FileEntry::name));
            return result;
        } catch (Exception e) {
            log.error("SFTP list failed [vm={}, path={}]: {}", tentacleName, remotePath, e.getMessage());
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }
