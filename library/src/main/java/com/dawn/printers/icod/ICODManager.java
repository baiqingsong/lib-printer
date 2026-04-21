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
        if (icodPrintUtil == null) {
            LLog.i("icodPrintUtil 为空，无法打印");
            mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, false, "打印机未初始化");
            return;
        }
        if (android.text.TextUtils.isEmpty(imagePath) || !new java.io.File(imagePath).exists()) {
            LLog.e("打印图片不存在：" + imagePath);
            mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, false, "图片不存在");
            return;
        }
        RxTask.runAsync(() -> {
            LLog.i("开始打印图片：" + imagePath);
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                LLog.e("图片解码失败：" + imagePath);
                mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, false, "图片解码失败");
                return;
            }
            try {
                icodPrintUtil.printBitmapA3(bitmap);
                icodPrintUtil.cutPaper();
                mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, true, "打印完成");
            } catch (Exception e) {
                LLog.e("ICOD 打印异常：" + e.getMessage());
                mPrinterCallbackListener.getPrintResult(PrinterType.THERMAL, false, "打印异常：" + e.getMessage());
            } finally {
                if (!bitmap.isRecycled()) bitmap.recycle();
            }
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
