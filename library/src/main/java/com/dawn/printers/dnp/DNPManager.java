package com.dawn.printers.dnp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.printers.R;
import com.dawn.printers.internal.RxTask;
import com.dawn.util_fun.LLog;
import com.saika.dnpprintersdk.model.PrintOrder;


/**
 * DNP 打印（Saika 新 SDK）。多份打印通过单次 {@code queuePrint} + {@link com.saika.dnpprintersdk.model.PrintOptions#setCopies}
 * 完成；与旧版逐张传图不同，上层传入的 {@code printNum} 即订单份数，不会循环多次提交。
 */
public class DNPManager extends PrinterManage {
    private DNPPrintFactory mDNPPrintFactory;
    private int dnpOffsetValue = 0;
    private int color = 0;
    private PrinterType currentPrinterType;
    private DnpPrinterType dnpPrintType = DnpPrinterType.RX1;
    private int currentNum;
    private String currentImagePath;
    private boolean currentIsCut;

    public DNPManager(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        super(context, mPrinterCallbackListener);
    }

    // ========== 统一打印回调（静态内部类，避免匿名类触发 D8 dexing bug）==========

    /**
     * DNP 打印订单回调，封装 Bitmap 回收和结果通知。
     * 使用静态内部类替代匿名类，避免 AGP 7.4.2 内置 D8 (R8 4.0.52) 处理
     * 多嵌套匿名类时的 NPE bug。
     */
    private static class DnpPrintCallback extends DnpSdkCallbackAdapters.OrderAdapter {
        private final Bitmap bitmap;
        private final PrinterType printerType;
        private final IPrinterCallbackListener listener;

        DnpPrintCallback(Bitmap bitmap, PrinterType printerType, IPrinterCallbackListener listener) {
            this.bitmap = bitmap;
            this.printerType = printerType;
            this.listener = listener;
        }

        @Override
        public void onOrderCompleted(PrintOrder order) {
            recycleBitmapQuietly(bitmap);
            if (listener != null) {
                listener.getPrintResult(printerType, true, "打印成功");
            }
            LLog.i("DNP print order completed, type=" + printerType);
        }

        @Override
        public void onOrderFailed(PrintOrder order, String message) {
            recycleBitmapQuietly(bitmap);
            if (listener != null) {
                listener.getPrintResult(printerType, false, message != null ? message : "打印失败");
            }
            LLog.e("DNP print order failed, type=" + printerType + ", msg=" + message);
        }

        @Override
        public void onOrderCancelled(PrintOrder order) {
            recycleBitmapQuietly(bitmap);
            if (listener != null) {
                listener.getPrintResult(printerType, false, "打印已取消");
            }
            LLog.e("DNP print order cancelled, type=" + printerType);
        }
    }

    // ========== 初始化 / 状态 ==========

    @Override
    public void initPrinter(PrinterType printerType) {
        currentPrinterType = printerType;
        RxTask.runAsync(() -> {
            if (mDNPPrintFactory == null) {
                mDNPPrintFactory = new DNPPrintFactory(context);
            }
            switch (currentPrinterType) {
                case DNP_RX1:  dnpPrintType = DnpPrinterType.RX1;   break;
                case DNP_620:  dnpPrintType = DnpPrinterType.DS620;  break;
                case DNP_410:  dnpPrintType = DnpPrinterType.QW410;  break;
                default:       dnpPrintType = DnpPrinterType.RX1;   break;
            }

            DnpPrinterType type = dnpPrintType;
            mDNPPrintFactory.initValue(type, () -> {
                String printStatus = mDNPPrintFactory.sendPrintStatus();
                if ("空闲".equals(printStatus)) {
                    mPrinterCallbackListener.initStatus(currentPrinterType, true, printStatus);
                    int printNum = mDNPPrintFactory.getPrintCount();
                    mPrinterCallbackListener.getPrinterCount(currentPrinterType, printNum, printStatus);
                } else {
                    mPrinterCallbackListener.initStatus(currentPrinterType, false, printStatus);
                }
            }, () -> mPrinterCallbackListener.initStatus(currentPrinterType, false, "DNP 连接失败"));
        });
    }

    @Override public void stop() { }
    @Override public void getStatus() { }

    // ========== 打印入口 ==========

    @Override
    public void startPrint(String imagePath, int printNum, boolean isCut) {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法打印");
            return;
        }
        currentNum = printNum;
        currentImagePath = imagePath;
        currentIsCut = isCut;
        enqueueDnpPrintOrder();
    }

    /** 8 寸照片打印（6x8 英寸），QW410 不支持。 */
    public void startPrint8Inch(String imagePath, int printNum) {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法打印");
            return;
        }
        if (dnpPrintType == DnpPrinterType.QW410) {
            mPrinterCallbackListener.getPrintResult(currentPrinterType, false,
                    "8 inch print does not support DNP QW410");
            return;
        }
        currentNum = printNum;
        currentImagePath = imagePath;
        enqueueDnpPrintOrder8Inch();
    }

    // ========== 打印实现 ==========

    private void enqueueDnpPrintOrder() {
        RxTask.runAsync(() -> {
            if (mDNPPrintFactory.getConnection() == null || !mDNPPrintFactory.getConnection().isConnected()) {
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "打印机未连接");
                return;
            }
            try {
                if (!mDNPPrintFactory.getConnection().isReady()) {
                    mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "打印机未就绪");
                    return;
                }
            } catch (Exception e) {
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "打印机状态异常");
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(currentImagePath);
            if (bitmap == null) {
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "打印失败");
                return;
            }

            int copies = Math.max(1, currentNum);
            LLog.i("DNP 提交打印订单: 份数=" + copies + "，偏移=" + dnpOffsetValue + ", 颜色=" + color);

            final Bitmap bitmapToPrint = bitmap;
            mDNPPrintFactory.setPrintCallbacks(
                    new DnpPrintCallback(bitmapToPrint, currentPrinterType, mPrinterCallbackListener), null);

            boolean ok = mDNPPrintFactory.printImage(context, dnpPrintType, bitmapToPrint, color, dnpOffsetValue,
                    currentIsCut, copies);
            if (!ok) {
                recycleBitmapQuietly(bitmapToPrint);
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "图像发送失败");
            }
        });
    }

    private void enqueueDnpPrintOrder8Inch() {
        RxTask.runAsync(() -> {
            int copies = Math.max(1, currentNum);
            LLog.i("DNP 提交 8 寸打印订单: 份数=" + copies);

            DnpPrintCallback cb = new DnpPrintCallback(null, currentPrinterType, mPrinterCallbackListener);
            mDNPPrintFactory.setPrintCallbacks(cb, null);

            boolean ok = mDNPPrintFactory.enqueuePrint8InchFromFile(currentImagePath, copies, cb);
            if (!ok) {
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false,
                        mDNPPrintFactory.getLastSubmitError());
            }
        });
    }

    // ========== 测试打印 ==========

    @Override
    public void printTest() {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法打印");
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pic1844x1240);
        if (bitmap != null) {
            final Bitmap bitmapToPrint = bitmap;
            mDNPPrintFactory.setPrintCallbacks(
                    new DnpPrintCallback(bitmapToPrint, currentPrinterType, mPrinterCallbackListener), null);
            boolean ok = mDNPPrintFactory.printTestImage(dnpPrintType, bitmapToPrint, dnpOffsetValue);
            if (!ok) {
                recycleBitmapQuietly(bitmapToPrint);
            }
        }
    }

    /** 8 寸测试打印（6x8 英寸），使用内置 pic1844x2434 测试图。 */
    public void printTest8Inch() {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法打印");
            return;
        }
        if (dnpPrintType == DnpPrinterType.QW410) {
            mPrinterCallbackListener.getPrintResult(currentPrinterType, false,
                    "8 inch test print does not support DNP QW410");
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.pic1844x2434);
        if (bitmap != null) {
            final Bitmap bitmapToPrint = bitmap;
            mDNPPrintFactory.setPrintCallbacks(
                    new DnpPrintCallback(bitmapToPrint, currentPrinterType, mPrinterCallbackListener), null);
            boolean ok = mDNPPrintFactory.printTestImage8Inch(dnpPrintType, bitmapToPrint, dnpOffsetValue);
            if (!ok) {
                recycleBitmapQuietly(bitmapToPrint);
            }
        }
    }

    // ========== 查询 ==========

    @Override
    public void getPrintCount() {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法查询打印计数");
            return;
        }
        String printStatus = mDNPPrintFactory.sendPrintStatus();
        LLog.i("DNP 打印机状态: " + printStatus);
        int printNum = mDNPPrintFactory.getPrintCount();
        mPrinterCallbackListener.getPrinterCount(currentPrinterType, printNum, printStatus);
    }

    // ========== 参数设置 ==========

    public void setDnpOffsetValue(int offsetValue) {
        if (offsetValue == 0) return;
        this.dnpOffsetValue = offsetValue;
    }

    public void setColor(int color) {
        if (color == 0) return;
        this.color = color;
    }
}
