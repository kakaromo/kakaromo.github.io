// @source src/main/java/com/samsung/move/logbrowser/service/LogBrowserService.java
// @lines 1-33
// @note 공통 인터페이스 + FileEntry/FileContent/SearchResult record
// @synced 2026-04-19T08:19:17.633Z

package com.samsung.move.logbrowser.service;

import java.io.InputStream;
import java.util.List;

public interface LogBrowserService {

    List<FileEntry> listFiles(String tentacleName, String remotePath);

    InputStream downloadFile(String tentacleName, String remotePath);

    String readFileContent(String tentacleName, String remotePath);

    FileContent readLines(String tentacleName, String path, int startLine, int limit);

    FileContent readLastLines(String tentacleName, String path, int lineCount);

    List<SearchResult> searchInFile(String tentacleName, String path, String pattern);

    boolean isBinaryFile(String tentacleName, String path);

    void uploadFile(String tentacleName, String remotePath, InputStream inputStream, String fileName);

    void deleteFile(String tentacleName, String remotePath);

    default int getActiveSessionCount() { return 0; }

    record FileEntry(String name, boolean directory, long size, long lastModified) {}

    record FileContent(String content, int startLine, int totalLines) {}

    record SearchResult(int lineNumber, String text) {}
}
