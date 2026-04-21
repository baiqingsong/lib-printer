package com.dawn.printers.uv;

import android.content.Context;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.printers.uv.model.PrintStatusResultModel;
import com.dawn.printers.uv.model.PrinterStatusResultModel;

public class UVManager extends PrinterManage {
    // 192.168.31.131:63333
    public UVManager(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        super(context, mPrinterCallbackListener);
    }
    private UVPrintUtil uvPrintUtil;
    private boolean printStatus;
    private String printMsg;

    @Override
    public void initPrinter(PrinterType printerType) {
        uvPrintUtil = new UVPrintUtil();
        uvPrintUtil.startTcpClient("192.168.31.131", 63333);
        uvPrintUtil.setOnPrinterStatusListener(new UVPrintUtil.OnPrinterStatusListener() {
            @Override
            public void onStatusResult(PrinterStatusResultModel statusResult) {
                try{
                    printStatus = "1".equals(statusResult.getStatus());
                    printMsg = statusResult.getMsg();
                    mPrinterCallbackListener.initStatus(PrinterType.UV, printStatus, printMsg);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onPrintResult(PrintStatusResultModel statusResultModel) {
                try{
                    if("2".equals(statusResultModel.getStatus())) {
                        mPrinterCallbackListener.getPrintResult(PrinterType.UV, true, "打印成功");
                    }
                }catch (Exception e){
                    e.printStackTrace();
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
