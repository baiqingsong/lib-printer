package com.dawn.printers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public abstract class PrinterManage {
    protected Context context;
    protected IPrinterCallbackListener mPrinterCallbackListener;
    protected static final int photo_default_width  = 1240;
    protected static final int photo_default_height = 1844;

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

    // ------------------------------------------------------------------ //
    //  通用工具方法                                                         //
    // ------------------------------------------------------------------ //

    /**
     * 将 Bitmap 缩放到目标尺寸（保持比例填充，超出部分裁剪居中），返回新 Bitmap。
     * 原 Bitmap 若与新对象不同会被自动 recycle。
     *
     * @param src    源 Bitmap（调用后所有权转移，不得再使用）
     * @param width  目标宽度（px）
     * @param height 目标高度（px）
     */
    protected static Bitmap scaleBitmapCenter(Bitmap src, int width, int height) {
        if (src == null) return null;
        if (src.getWidth() == width && src.getHeight() == height) return src;

        float scaleX = (float) width  / src.getWidth();
        float scaleY = (float) height / src.getHeight();
        float scale  = Math.max(scaleX, scaleY);

        int scaledW = Math.round(src.getWidth()  * scale);
        int scaledH = Math.round(src.getHeight() * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
        if (scaled != src) src.recycle();

        int offsetX = (scaledW - width)  / 2;
        int offsetY = (scaledH - height) / 2;
        Bitmap cropped = Bitmap.createBitmap(scaled, offsetX, offsetY, width, height);
        if (cropped != scaled) scaled.recycle();
        return cropped;
    }

    /**
     * 安静回收 Bitmap，忽略 null 和已回收的情况。
     */
    protected static void recycleBitmapQuietly(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    /**
     * 在白色背景画布上合成 Bitmap（用于打印机需要纯白背景的场景）。
     */
    protected static Bitmap compositeOnWhite(Bitmap src, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);
        if (src != null) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(src, (width - src.getWidth()) / 2f, (height - src.getHeight()) / 2f, paint);
        }
        return output;
    }
}
