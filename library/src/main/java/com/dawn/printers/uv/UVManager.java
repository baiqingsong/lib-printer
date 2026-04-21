package com.dawn.printers.uv;

import android.content.Context;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.printers.uv.model.PrintStatusResultModel;
import com.dawn.printers.uv.model.PrinterStatusResultModel;
import com.dawn.util_fun.LLog;

public class UVManager extends PrinterManage {
    /** 默认 UV 打印机地址 */
    private static final String DEFAULT_UV_HOST = "192.168.31.131";
    private static final int    DEFAULT_UV_PORT = 63333;

    private String uvHost;
    private int    uvPort;

    public UVManager(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        super(context, mPrinterCallbackListener);
        this.uvHost = DEFAULT_UV_HOST;
        this.uvPort = DEFAULT_UV_PORT;
    }

    /**
     * 允许为测试或参数化地址联接
     */
    public UVManager(Context context, IPrinterCallbackListener listener, String host, int port) {
        super(context, listener);
        this.uvHost = host;
        this.uvPort = port;
    }

    private UVPrintUtil uvPrintUtil;
    private boolean printStatus;
    private String printMsg;

    @Override
    public void initPrinter(PrinterType printerType) {
        // 已初始化且 TCP 连接存活，无需重建
        if (uvPrintUtil != null) {
            LLog.i("UV 打印机已初始化，跳过重建");
            return;
        }
        uvPrintUtil = new UVPrintUtil();
        uvPrintUtil.startTcpClient(uvHost, uvPort);
        uvPrintUtil.setOnPrinterStatusListener(new UVPrintUtil.OnPrinterStatusListener() {
            @Override
            public void onStatusResult(PrinterStatusResultModel statusResult) {
                try {
                    printStatus = "1".equals(statusResult.getStatus());
                    printMsg = statusResult.getMsg();
                    mPrinterCallbackListener.initStatus(PrinterType.UV, printStatus, printMsg);
                } catch (Exception e) {
                    LLog.e("UV 状态回调异常：" + e.getMessage());
                }
            }

            @Override
            public void onPrintResult(PrintStatusResultModel statusResultModel) {
                try {
                    // status: 2=成功, 其他均为失败
                    boolean success = "2".equals(statusResultModel.getStatus());
                    String msg = statusResultModel.getMsg() != null ? statusResultModel.getMsg()
                            : (success ? "打印成功" : "打印失败，code=" + statusResultModel.getStatus());
                    mPrinterCallbackListener.getPrintResult(PrinterType.UV, success, msg);
                } catch (Exception e) {
                    LLog.e("UV 打印结果回调异常：" + e.getMessage());
                }
            }
        });
    }

    @Override
    public void stop() {
        if(uvPrintUtil != null){
            uvPrintUtil.stopTcp();
        }
    }

    @Override
    public void getStatus() {

    }

    @Override
    public void startPrint(String imagePath, int printNum, boolean isCut) {

    }

    @Override
    public void printTest() {

    }

    @Override
    public void getPrintCount() {

    }

    public void printImage(Context context, String imagePath, int channel, float width, float height, float left, float top){
        if(uvPrintUtil != null){
            uvPrintUtil.sendPrintImage(context, imagePath, channel, width, height, left, top);
        }
    }

    /**
     * 测试打印
     * @param channel 货道
     */
    public void printTest(Context context, int resId, int channel, float width, float height, float left, float top){
        if(uvPrintUtil != null){
            uvPrintUtil.sendPrintTest(context, resId, channel, width, height, left, top);
        }
    }

    /**
     * 清洗喷嘴
     */
    public void cleanPrintHeader(){
        if(uvPrintUtil != null){
            uvPrintUtil.sendCleanPrintHead();
        }
    }

    /**
     * 检查喷嘴
     */
    public void checkPrintHeader(){
        if(uvPrintUtil != null){
            uvPrintUtil.sendCheckPrintHead();
        }
    }
}
