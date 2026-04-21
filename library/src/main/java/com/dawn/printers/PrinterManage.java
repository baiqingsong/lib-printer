package com.dawn.printers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.dawn.util_fun.LLog;

import io.reactivex.rxjava3.disposables.Disposable;

public abstract class PrinterManage {
    protected Context context;
    protected IPrinterCallbackListener mPrinterCallbackListener;
    protected final static int photo_default_width = 1240;
    protected final static int photo_default_height = 1844;
    private Disposable countdown;

    public PrinterManage(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        this.context = context;
        this.mPrinterCallbackListener = mPrinterCallbackListener;
    }

    public abstract void initPrinter(PrinterType printerType);

    public abstract void stop();

    public abstract void getStatus();


    public abstract void startPrint(String imagePath, int printNum, boolean isCut);

    public abstract void printTest();

    public abstract void getPrintCount();

}
