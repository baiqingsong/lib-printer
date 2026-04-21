package com.dawn.printers.dnp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;

import com.saika.dnpprintersdk.DNPPrinterSDK;
import com.saika.dnpprintersdk.api.PrinterConnection;
import com.saika.dnpprintersdk.api.PrinterManager;
import com.saika.dnpprintersdk.api.callback.OrderCallback;
import com.saika.dnpprintersdk.api.callback.PrintCallback;
import com.saika.dnpprintersdk.config.SDKConfig;
import com.saika.dnpprintersdk.exception.ConnectionException;
import com.saika.dnpprintersdk.model.ColorAdjustment;
import com.saika.dnpprintersdk.model.CutterMode;
import com.saika.dnpprintersdk.model.FinishType;
import com.saika.dnpprintersdk.model.MediaInfo;
import com.saika.dnpprintersdk.model.MediaValidationConfig;
import com.saika.dnpprintersdk.model.PaperSize;
import com.saika.dnpprintersdk.model.PrintOptions;
import com.saika.dnpprintersdk.model.PrintSpeed;
import com.saika.dnpprintersdk.model.PrinterInfo;
import com.saika.dnpprintersdk.model.PrinterModel;
import com.saika.dnpprintersdk.model.PrinterStatus;
import com.dawn.util_fun.RxTask;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DNP 打印封装，基于 {@link DNPPrinterSDK}。
 */
public class DNPPrintFactory {

    @SuppressLint("StaticFieldLeak")
    private static DNPPrintFactory instance;
    private final Context mContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PrinterManager printerManager;
    private PrinterConnection connection;


    public DNPPrintFactory(Context context) {
        mContext = context.getApplicationContext();
    }

    private OrderCallback pendingOrderCallback;
    private PrintCallback pendingPrintCallback;

    public PrinterConnection getConnection() {
        return connection;
    }

    private static void ensureSdk(Context context) {
        if (!DNPPrinterSDK.getInstance().isInitialized()) {
            SDKConfig config = new SDKConfig.Builder()
                    .autoConnect(false)
                    .maxRetries(3)
                    .build();
            DNPPrinterSDK.getInstance().initialize(context.getApplicationContext(), config);
        }
    }

    /**
     * @param onReady  已连接且可查询状态（主线程）
     * @param onFailed 初始化或连接失败（主线程）
     */
    public void initValue(DnpPrinterType printType, Runnable onReady, Runnable onFailed) {
        AtomicBoolean readyOnce = new AtomicBoolean(false);

        Runnable notifyReady = () -> {
            if (readyOnce.compareAndSet(false, true) && onReady != null) {
                mainHandler.post(onReady);
            }
        };
        Runnable notifyFail = () -> {
            if (onFailed != null) {
                mainHandler.post(onFailed);
            }
        };

        // 与 DNPManager 一致使用 IO 线程池，避免每次 init 新建 Thread；SDK 回调仍由厂商线程触发
        RxTask.runAsync(() -> {
            try {
                ensureSdk(mContext);
                printerManager = DNPPrinterSDK.getInstance().getPrinterManager();
                PrinterModel wantModel = toPrinterModel(printType);

                printerManager.setConnectionCallback(new DnpSdkCallbackAdapters.ConnectionAdapter() {
                    @Override
                    public void onConnected(String printerId, PrinterInfo info) {
                        connection = printerManager.getConnection(printerId);
                        applyConnectionDefaults(connection);
                        notifyReady.run();
                    }

                    @Override
                    public void onConnectionError(String printerId, int errorCode, String message) {
                        notifyFail.run();
                    }

                    @Override
                    public void onDisconnected(String printerId, String reason) {
                        connection = null;
                    }
                });

                printerManager.startDiscovery();

                UsbDevice dev = findBestDevice(wantModel);
                PrinterConnection conn;
                if (dev != null) {
                    conn = printerManager.connect(dev);
                } else {
                    conn = printerManager.connectFirst();
                }

                if (conn != null) {
                    connection = conn;
                    applyConnectionDefaults(connection);
                    notifyReady.run();
                }
            } catch (ConnectionException e) {
                notifyFail.run();
            } catch (Exception e) {
                notifyFail.run();
            }
        });
    }

    private static PrinterModel toPrinterModel(DnpPrinterType printType) {
        switch (printType) {
            case DS620:
                return PrinterModel.DS620;
            case QW410:
                return PrinterModel.QW410;
            case RX1:
            default:
                return PrinterModel.DS_RX1;
        }
    }

    private UsbDevice findBestDevice(PrinterModel preferred) {
        if (printerManager == null) {
            return null;
        }
        List<UsbDevice> devices = printerManager.scanDevices();
        if (devices == null) {
            return null;
        }
        for (UsbDevice d : devices) {
            if (PrinterModel.fromUsbIds(d.getVendorId(), d.getProductId(), null) == preferred) {
                return d;
            }
        }
        for (UsbDevice d : devices) {
            if (PrinterModel.isDNPPrinter(d.getVendorId(), d.getProductId())) {
                return d;
            }
        }
        return null;
    }

    private void applyConnectionDefaults(PrinterConnection conn) {
        MediaValidationConfig validationConfig = MediaValidationConfig.builder()
                .lowMediaThreshold(10)
                .insufficientMediaPolicy(MediaValidationConfig.InsufficientMediaPolicy.WARN)
                .incompatibleSizePolicy(MediaValidationConfig.IncompatibleSizePolicy.BLOCK)
                .enableValidation(true)
                .build();
        conn.setMediaValidationConfig(validationConfig);
        if (pendingOrderCallback != null) {
            conn.setOrderCallback(pendingOrderCallback);
        }
        if (pendingPrintCallback != null) {
            conn.setPrintCallback(pendingPrintCallback);
        }
    }

    /**
     * 在提交打印前设置订单与任务回调（可多次调用覆盖）。
     */
    public void setPrintCallbacks(OrderCallback orderCallback, PrintCallback printCallback) {
        pendingOrderCallback = orderCallback;
        pendingPrintCallback = printCallback;
        if (connection != null) {
            if (orderCallback != null) {
                connection.setOrderCallback(orderCallback);
            }
            if (printCallback != null) {
                connection.setPrintCallback(printCallback);
            }
        }
    }

    public void setCutterMode(int mode) {
        // 旧 API：0 标准 / 1 两英寸裁切；新 SDK 在每次 queuePrint 的 PrintOptions 中设置
    }

    public String sendPrintStatus() {
        if (connection == null || !connection.isConnected()) {
            return "断开";
        }
        try {
            PrinterStatus status = connection.getStatus();
            return mapStatusToLegacyChinese(status);
        } catch (ConnectionException e) {
            return "断开";
        }
    }

    private static String mapStatusToLegacyChinese(PrinterStatus status) {
        if (status == null) {
            return "其它异常";
        }
        if (status == PrinterStatus.IDLE || status == PrinterStatus.STANDBY) {
            return "空闲";
        }
        if (status == PrinterStatus.PRINTING) {
            return "正在打印...";
        }
        if (status == PrinterStatus.PAPER_END) {
            return "缺纸";
        }
        if (status == PrinterStatus.RIBBON_END) {
            return "缺色带";
        }
        if (status == PrinterStatus.HEAD_COOLING || status == PrinterStatus.MOTOR_COOLING
                || status == PrinterStatus.COOLING) {
            return "冷却";
        }
        if (status == PrinterStatus.COVER_OPEN) {
            return "盖子打开";
        }
        if (status == PrinterStatus.PAPER_JAM) {
            return "卡纸";
        }
        if (status == PrinterStatus.RIBBON_ERROR) {
            return "色带异常";
        }
        if (status == PrinterStatus.DATA_ERROR) {
            return "数据异常";
        }
        return status.getDescription() != null ? status.getDescription() : "其它异常";
    }

    public int getPrintCount() {
        try {
            if (connection == null) {
                return -1;
            }
            MediaInfo media = connection.getMediaInfo();
            return media != null ? media.getRemainingPrints() : -1;
        } catch (ConnectionException e) {
            return -1;
        }
    }

    public int getPrintCountH() {
        return getPrintCount();
    }

    /**
     * 提交一次打印订单。新 SDK 下多份为同一订单内的 {@link PrintOptions#setCopies(int)}，
     * 只调用一次 {@link PrinterConnection#queuePrint}；旧版 JNI 需逐张传图，已由本方法合并为单任务。
     *
     * @param copies 打印份数（至少为 1），对应 {@link PrintOptions#setCopies}
     */
    public boolean printImage(Context context, DnpPrinterType printType, Bitmap bitmap, int rawType, int dnpOffsetValue, boolean isCut, int copies) {
        if (connection == null || !connection.isConnected() || bitmap == null) {
            return false;
        }
        PrintOptions options = buildPrintOptions(printType, rawType, isCut, copies);
        if (pendingOrderCallback != null) {
            connection.setOrderCallback(pendingOrderCallback);
        }
        if (pendingPrintCallback != null) {
            connection.setPrintCallback(pendingPrintCallback);
        }
        connection.queuePrint(bitmap, options);
        return true;
    }

    private PrintOptions buildPrintOptions(DnpPrinterType printType, int colorPreset, boolean isCut, int copies) {
        PaperSize paper = printType == DnpPrinterType.QW410 ? PaperSize.SIZE_4X6 : PaperSize.SIZE_6X4;
        CutterMode cutter = isCut ? CutterMode.TWO_INCH : CutterMode.NORMAL;
        PrintSpeed speed = printType == DnpPrinterType.QW410 ? PrintSpeed.STANDARD : PrintSpeed.STANDARD;

        int copyCount = Math.max(1, copies);
        PrintOptions options = new PrintOptions()
                .setPaperSize(paper)
                .setFinishType(FinishType.GLOSSY)
                .setPrintSpeed(speed)
                .setCutterMode(cutter)
                .setScaleMode(PrintOptions.ScaleMode.FILL_CROP)
                .setCopies(copyCount);

        ColorAdjustment adj = colorAdjustmentForPreset(colorPreset);
        if (adj != null) {
            options.setColorAdjustment(adj);
        }
        return options;
    }

    /**
     * 原 LUT 预设（1–7）与 USB raw（8）的简化替代：用 ColorAdjustment 区分档位。
     */
    private static ColorAdjustment colorAdjustmentForPreset(int rawType) {
        if (rawType <= 0) {
            return null;
        }
        ColorAdjustment c = new ColorAdjustment();
        switch (rawType) {
            case 1:
                c.setSaturation(1.05f);
                c.setContrast(1.02f);
                break;
            case 2:
                c.setSaturation(1.08f);
                c.setContrast(1.03f);
                break;
            case 3:
                c.setSaturation(1.1f);
                break;
            case 4:
                c.setSaturation(1.12f);
                c.setBrightness(0.02f);
                break;
            case 5:
                c.setSaturation(0.98f);
                c.setContrast(1.05f);
                break;
            case 6:
                c.setSaturation(1.06f);
                c.setRedGain(1.02f);
                break;
            case 7:
                c.setSaturation(1.1f);
                c.setBlueGain(0.98f);
                break;
            case 8:
            default:
                return null;
        }
        return c;
    }
}
