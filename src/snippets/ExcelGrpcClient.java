// @source src/main/java/com/samsung/move/testdb/excel/ExcelGrpcClient.java
// @lines 1-30
// @note GrpcChannelFactory → BlockingStub + generateExcel 빌더 호출
// @synced 2026-05-01T01:05:23.628Z

package com.samsung.move.testdb.excel;

import com.samsung.move.excel.ExcelServiceGrpc;
import com.samsung.move.excel.ExcelServiceProto.ExcelRequest;
import com.samsung.move.excel.ExcelServiceProto.ExcelResponse;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class ExcelGrpcClient {

    private final ExcelServiceGrpc.ExcelServiceBlockingStub stub;

    public ExcelGrpcClient(GrpcChannelFactory channels) {
        this.stub = ExcelServiceGrpc.newBlockingStub(channels.createChannel("excel-service"));
    }

    public ExcelResponse generateExcel(long parserId, String tcName, String fw, String setName, String fileSystem, String dataJson) {
        ExcelRequest request = ExcelRequest.newBuilder()
                .setParserId(parserId)
                .setTcName(tcName)
                .setFw(fw)
                .setDataJson(dataJson)
                .setSetName(setName)
                .setFileSystem(fileSystem != null ? fileSystem : "")
                .build();
        return stub.generateExcel(request);
    }
}

