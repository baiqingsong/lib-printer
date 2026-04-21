package com.dawn.printers;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dawn.printers.dnp.DNPManager;
import com.dawn.printers.event.ExternalPrintEvent;
import com.dawn.printers.event.PrintEvent;
import com.dawn.printers.hiti.HITIManager;
import com.dawn.printers.icod.ICODManager;
import com.dawn.printers.uv.UVManager;
import com.dawn.util_fun.LLog;
import com.dawn.util_fun.RxTask;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import io.reactivex.rxjava3.disposables.Disposable;

public class PrintService extends Service implements IPrinterCallbackListener {
    private final RemoteCallbackList<IPrinterAidlCallback> callbacks = new RemoteCallbackList<>();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IPrinterAidlInterface.Stub() {
            @Override
            public int getProcessId() {
                return Process.myPid();
            }

            @Override
            public void sendData(Bundle data) {
                if(data != null){
                    PrintEvent printEvent = (PrintEvent) data.getSerializable("printEvent");
                    if(printEvent != null) {
                        getPrintEvent(printEvent);
                    }
                }
            }

            @Override
            public void registerCallback(IPrinterAidlCallback callback) {
                callbacks.register(callback);
            }

            @Override
            public void unregisterCallback(IPrinterAidlCallback callback) {
                callbacks.unregister(callback);
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LLog.init(this, true, "dawn");
        LLog.e("PrintService onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LLog.e("PrintService onDestroy");
        // 逐一停止各打印机驱动，释放连接和线程资源
        stopAllManagers();
        callbacks.kill();
    }

    private void stopAllManagers() {
        try { if (hitiManager  != null) hitiManager.stop();  } catch (Exception e) { LLog.e("stop HITI 失败: " + e.getMessage()); }
        try { if (dnpManager   != null) dnpManager.stop();   } catch (Exception e) { LLog.e("stop DNP 失败: "  + e.getMessage()); }
        try { if (icodManager  != null) icodManager.stop();  } catch (Exception e) { LLog.e("stop ICOD 失败: " + e.getMessage()); }
        try { if (uvManager    != null) uvManager.stop();    } catch (Exception e) { LLog.e("stop UV 失败: "   + e.getMessage()); }
    }

    public void getPrintEvent(PrintEvent event){
        switch (event.getEvent()) {
            case INIT_PRINTER://设备初始化
                LLog.i("打印机初始化，类型：" + event.getPrinterType());
                initPrinter(event.getPrinterType());
                break;
            case GET_PRINTER_STATE:// 查询状态
                LLog.i("查询打印机状态，类型：" + event.getPrinterType());
                getPrinterState(event.getPrinterType());
                break;
            case GET_PRINT_COUNT:// 查询数量
                LLog.i("查询打印机剩余数量，类型：" + event.getPrinterType());
                getPrintCount(event.getPrinterType());
                break;
            case PRINT_IMAGE_TEST:// 打印测试页
                LLog.i("打印测试页，类型：" + event.getPrinterType());
                if (PrinterType.UV == event.getPrinterType()) {
                    printTestImage(event.getPrinterType(), event.getResId(), event.getChannel(), event.getWidth(), event.getHeight(), event.getLeft(), event.getTop());
                } else{
                    printTestImage(event.getPrinterType());
                }
                break;
            case PRINT_IMAGE:// 打印图片
                LLog.i("打印图片，类型：" + event.getPrinterType() + "，图片路径：" + event.getImagePath() + "，打印数量：" + event.getPrintNum() + "，是否切纸：" + event.isCut());
                if(PrinterType.UV == event.getPrinterType()){
                    printImage(event.getPrinterType(), event.getImagePath(), event.getChannel(), event.getWidth(), event.getHeight(), event.getLeft(), event.getTop());
                }else{
                    printImage(event.getPrinterType(), event.getImagePath(), event.getPrintNum(), event.isCut());
                }
                break;
            case PARAMETER_SETTING://参数设置
                switch (event.getPrinterType()){
                    case DNP_RX1:
                    case DNP_410:
                    case DNP_620:
                        if(dnpManager != null){
                            dnpManager.setDnpOffsetValue(event.getOffsetX());
                            dnpManager.setColor(event.getColor());
                        }
                        break;
                    case HITI:
                        if(hitiManager != null)
                            hitiManager.printRibbonCalibration();// 色带校验
                        break;
                    case UV:
                        if(uvManager != null){
                            if(event.getAction() == 9){
                                uvManager.cleanPrintHeader();// 清洗喷嘴
                            }else if(event.getAction() == 10){
                                uvManager.checkPrintHeader();// 打印头检测
                            }
                        }
                        break;
                }
                break;
        }
    }

    private HITIManager hitiManager;// 呈研打印机管理器
    private DNPManager dnpManager;// DNP 打印机管理器
    private ICODManager icodManager;// ICOD 打印机管理器
    private UVManager uvManager;// UV 打印机管理器

    /**
     * 打印机初始化
     * @param printerType 打印机类型
     */
    private void initPrinter(PrinterType printerType) {
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager == null){
                    hitiManager = new HITIManager(this, this);
                }
                LLog.i("初始化 HITI 打印机");
                hitiManager.initPrinter(printerType);
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager == null){
                    dnpManager = new DNPManager(this, this);
                }
                dnpManager.initPrinter(printerType);
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager == null){
                    icodManager = new ICODManager(this, this);
                }
                icodManager.initPrinter(printerType);
                break;
            case UV:
                if (uvManager == null) {
                    uvManager = new UVManager(this, this);
                }
                uvManager.initPrinter(printerType);
                break;
        }
    }

    /**
     * 查询打印数量
     * @param printerType 打印机类型
     */
    private void getPrintCount(PrinterType printerType){
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager != null){
                    hitiManager.getPrintCount();
                }
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager != null){
                    dnpManager.getPrintCount();
                }
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager != null){
                    icodManager.getPrintCount();
                }
                break;
        }
    }

    /**
     * 查询打印机状态
     * @param printerType 打印机类型
     */
    private void getPrinterState(PrinterType printerType){
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager != null){
                    hitiManager.getStatus();
                }
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager != null){
                    dnpManager.getStatus();
                }
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager != null){
                    icodManager.getStatus();
                }
                break;
        }
    }

    /**
     * 打印测试页
     * @param printerType 打印机类型
     */
    private void printTestImage(PrinterType printerType){
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager != null){
                    hitiManager.printTest();
                }
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager != null){
                    dnpManager.printTest();
                }
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager != null){
                    icodManager.printTest();
                }
                break;
        }
    }

    private void printTestImage(PrinterType printerType, int resId, int channel, float width, float height, float left, float top){
        if(PrinterType.UV == printerType && uvManager != null){
            uvManager.printTest(this, resId, channel, width, height, left, top);
        }
    }




    /**
     * 打印图片
     * @param printerType 打印机类型
     * @param imagePath 图片地址
     * @param printNum 打印数量
     * @param isCut 是否切纸
     */
    private void printImage(PrinterType printerType, String imagePath, int printNum, boolean isCut){
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager != null){
                    hitiManager.startPrint(imagePath, printNum, isCut);

                }
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager != null){
                    dnpManager.startPrint(imagePath, printNum, isCut);

                }
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager != null){
                    icodManager.startPrint(imagePath, printNum, isCut);
                }
                break;
        }
    }

    /**
     * UV 打印图片
     * @param printerType 打印机类型
     * @param imagePath 图片地址
     * @param channel 打印通道
     * @param width 打印宽度
     * @param height 打印高度
     * @param left 打印左边距
     * @param top 打印上边距
     */
    private void printImage(PrinterType printerType, String imagePath, int channel, float width, float height, float left, float top){
        if(PrinterType.UV == printerType && uvManager != null){
            uvManager.printImage(this, imagePath, channel, width, height, left, top);
        }
    }

    /**
     * 停止打印机
     * @param printerType 打印机类型
     */
    private void stopPrinter(PrinterType printerType){
        switch (printerType){
            case HITI:// 呈研
                if(hitiManager != null){
                    hitiManager.stop();
                }
                break;
            case DNP_RX1:// dnp
            case DNP_620:
            case DNP_410:
                if(dnpManager != null){
                    dnpManager.stop();
                }
                break;
            case THERMAL:// ICOD 热敏打印机
                if(icodManager != null){
                    icodManager.stop();
                }
                break;
        }
    }

    private void stopPrinter(){
        if(hitiManager != null){
            hitiManager.stop();
        }
        if(dnpManager != null){
            dnpManager.stop();
        }
        if(icodManager != null){
            icodManager.stop();
        }
    }

    @Override
    public void initStatus(PrinterType printerType, boolean status, String msg) {
        LLog.e("打印机初始化回调：" + printerType + "，状态：" + status + "，消息：" + msg);
        sendMsg(new ExternalPrintEvent(ExternalPrintEvent.EventType.GET_STATUS, status, msg));
    }

    @Override
    public void getPrinterCount(PrinterType printerType, int remainPaper, String msg) {
        LLog.e("打印机数量回调：" + printerType + "，剩余数量：" + remainPaper + "，消息：" + msg);
        sendMsg(new ExternalPrintEvent(ExternalPrintEvent.EventType.GET_PRINT_COUNT, remainPaper, msg));
    }

    @Override
    public void getPrintResult(PrinterType printerType, boolean status, String msg) {
        LLog.e("打印结果回调：" + printerType + "，状态：" + status + "，消息：" + msg);
        sendMsg(new ExternalPrintEvent(ExternalPrintEvent.EventType.PRINT_IMAGE, status, msg));
        LLog.i("打印完成，关闭打印倒计时");
    }

    private void sendMsg(ExternalPrintEvent event) {
        try {
            Bundle bundle = new Bundle();
            bundle.putSerializable("externalPrintEvent", event);
            int count = callbacks.beginBroadcast();
            for (int i = 0; i < count; i++) {
                callbacks.getBroadcastItem(i).onDataReceived(bundle);
            }
            callbacks.finishBroadcast();
        } catch (Exception e) {
            LLog.e("发送消息失败：" + e.getMessage());
        }
    }
}
