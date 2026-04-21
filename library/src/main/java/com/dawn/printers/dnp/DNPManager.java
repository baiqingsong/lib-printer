package com.dawn.printers.dnp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.printers.R;
import com.dawn.util_fun.RxTask;
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

    @Override
    public void initPrinter(PrinterType printerType) {
        currentPrinterType = printerType;
        RxTask.runAsync(() -> {
            if (mDNPPrintFactory == null) {
                mDNPPrintFactory = new DNPPrintFactory(context);
            }
            switch (currentPrinterType) {
                case DNP_RX1:
                    dnpPrintType = DnpPrinterType.RX1;
                    break;
                case DNP_620:
                    dnpPrintType = DnpPrinterType.DS620;
                    break;
                case DNP_410:
                    dnpPrintType = DnpPrinterType.QW410;
                    break;
                default:
                    dnpPrintType = DnpPrinterType.RX1;
                    break;
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

    @Override
    public void stop() {
    }

    @Override
    public void getStatus() {
    }

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

    /** 解码图片并发起单次 SDK 打印订单（份数由 {@link #currentNum} 经 setCopies 体现）。 */
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
            LLog.i("DNP 提交打印订单: 份数=" + copies + "（单次 queuePrint + setCopies），偏移=" + dnpOffsetValue + ", 颜色=" + color);

            // queuePrint 异步处理 Bitmap，禁止在 printImage 返回后立即 recycle，否则 SDK 报
            // "cannot use a recycled source in createBitmap"
            final Bitmap bitmapToPrint = bitmap;
            mDNPPrintFactory.setPrintCallbacks(
                    new DnpSdkCallbackAdapters.OrderAdapter() {
                        @Override
                        public void onOrderCompleted(PrintOrder order) {
                            recycleBitmapQuietly(bitmapToPrint);
                            mPrinterCallbackListener.getPrintResult(currentPrinterType, true, "打印成功");
                        }

                        @Override
                        public void onOrderFailed(PrintOrder order, String message) {
                            recycleBitmapQuietly(bitmapToPrint);
                            mPrinterCallbackListener.getPrintResult(currentPrinterType, false,
                                    message != null ? message : "打印失败");
                        }

                        @Override
                        public void onOrderCancelled(PrintOrder order) {
                            recycleBitmapQuietly(bitmapToPrint);
                            mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "打印已取消");
                        }
                    },
                    null
            );

            boolean ok = mDNPPrintFactory.printImage(context, dnpPrintType, bitmapToPrint, color, dnpOffsetValue,
                    currentIsCut, copies);
            if (!ok) {
                recycleBitmapQuietly(bitmapToPrint);
                mPrinterCallbackListener.getPrintResult(currentPrinterType, false, "图像发送失败");
            }
        });
    }

    @Override
    public void printTest() {
        if (mDNPPrintFactory == null) {
            LLog.i("打印机未初始化，无法打印");
            return;
        }
        // printTest 仅用于调试，实际使用请通过 printImage() 传入图片路径
        Bitmap bitmap = null;
        if (bitmap != null) {
            final Bitmap bitmapToPrint = bitmap;
            mDNPPrintFactory.setPrintCallbacks(new DnpSdkCallbackAdapters.OrderAdapter() {
                @Override
                public void onOrderCompleted(PrintOrder order) {
                    recycleBitmapQuietly(bitmapToPrint);
                }

                @Override
                public void onOrderFailed(PrintOrder order, String message) {
                    recycleBitmapQuietly(bitmapToPrint);
                }

                @Override
                public void onOrderCancelled(PrintOrder order) {
                    recycleBitmapQuietly(bitmapToPrint);
                }
            }, null);
            boolean ok = mDNPPrintFactory.printImage(context, dnpPrintType, bitmapToPrint, -1, dnpOffsetValue, false, 1);
            if (!ok) {
                recycleBitmapQuietly(bitmapToPrint);
            }
        }
    }

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

    public void setDnpOffsetValue(int offsetValue) {
        if (offsetValue == 0) {
            return;
        }
        this.dnpOffsetValue = offsetValue;
    }

    public void setColor(int color) {
        if (color == 0) {
            return;
        }
        this.color = color;
    }
}
