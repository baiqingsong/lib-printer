package com.dawn.printers.icod;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.util_fun.RxTask;
import com.dawn.util_fun.LLog;

public class ICODManager extends PrinterManage {
    private IcodPrintUtil icodPrintUtil;
    public ICODManager(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        super(context, mPrinterCallbackListener);
    }

    @Override
    public void initPrinter(PrinterType printerType) {
        icodPrintUtil = new IcodPrintUtil(context);
        //判断打印机是否连接
        boolean isConnected = icodPrintUtil.isConnected();
        LLog.i("热敏打印机连接状态：" + isConnected);
        if (!isConnected) {
            icodPrintUtil.connect();
        }
        mPrinterCallbackListener.initStatus(PrinterType.THERMAL, icodPrintUtil.isConnected(), isConnected ? "热敏打印机连接成功" : "热敏打印机连接失败");
    }

    @Override
    public void stop() {

    }

    @Override
    public void getStatus() {

    }

    @Override
    public void startPrint(String imagePath, int printNum, boolean isCut) {
        if(icodPrintUtil == null){
            LLog.i("icodPrintUtil为空，无法打印");
            return;
        }
        RxTask.runAsync(() -> {
            LLog.i("开始打印图片：" + imagePath);
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            icodPrintUtil.printBitmapA3(bitmap);
            icodPrintUtil.cutPaper();
            mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, true, "打印完成");
        });
    }

    @Override
    public void printTest() {
        if(icodPrintUtil == null){
            LLog.i("icodPrintUtil为空，无法打印测试页");
            return;
        }
        icodPrintUtil.printSelfPage();
    }

    @Override
    public void getPrintCount() {

    }
}
