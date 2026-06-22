// @source src/main/java/com/samsung/move/logbrowser/service/LogBrowserService.java
// @lines 1-33
// @note 공통 인터페이스 + FileEntry/FileContent/SearchResult record
// @synced 2026-06-22T22:22:10.915Z

package com.samsung.move.logbrowser.service;

import java.io.InputStream;
import java.util.List;

public interface LogBrowserService {

    List<FileEntry> listFiles(String tentacleName, String remotePath);

    InputStream downloadFile(String tentacleName, String remotePath);

    String readFileContent(String tentacleName, String remotePath);

    FileContent readLines(String tentacleName, String path, int startLine, int limit);

    FileContent readLastLines(String tentacleName, String path, int lineCount);

    List<SearchResult> searchInFile(String tentacleName, String path, String pattern, boolean regex);

    /** Backward compatible: 기본 리터럴 검색. */
    default List<SearchResult> searchInFile(String tentacleName, String path, String pattern) {
        return searchInFile(tentacleName, path, pattern, false);
    }

    boolean isBinaryFile(String tentacleName, String path);

    void uploadFile(String tentacleName, String remotePath, InputStream inputStream, String fileName);

    void deleteFile(String tentacleName, String remotePath);

    default int getActiveSessionCount() { return 0; }

    record FileEntry(String name, boolean directory, long size, long lastModified) {}
