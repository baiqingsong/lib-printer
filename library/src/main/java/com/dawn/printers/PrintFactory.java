package com.dawn.printers;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.dawn.printers.event.ExternalPrintEvent;
import com.dawn.printers.event.PrintEvent;
import com.dawn.util_fun.LLog;
import com.dawn.util_fun.RxTask;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import ZtlApi.ZtlManager;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * 打印机门面（单例）。
 * 通过 AIDL 与独立进程 {@link PrintService} 通信；打印结果以 {@link com.dawn.printers.event.ExternalPrintEvent} 形式
 * 通过 EventBus 回调给调用方。
 */
public class PrintFactory {
    private PrintFactory() {}

    private static volatile PrintFactory instance;

    /** 双重检查锁，保证多线程安全 */
    public static PrintFactory getInstance() {
        if (instance == null) {
            synchronized (PrintFactory.class) {
                if (instance == null) {
                    instance = new PrintFactory();
                }
            }
        }
        return instance;
    }
    private PrinterType currentPrinterType;//当前打印机类型
    private IPrinterAidlInterface iIPrinterAidlInterface;// AIDL接口
    private int printPid;// 打印服务进程ID
    private final IPrinterAidlCallback.Stub aidlCallback = new IPrinterAidlCallback.Stub(){

        @Override
        public void onDataReceived(Bundle data) {
            if(data != null){
                ExternalPrintEvent externalPrintEvent = (ExternalPrintEvent) data.getSerializable("externalPrintEvent");
                if(externalPrintEvent != null) {
                    getPrinterServiceMsg(externalPrintEvent);
                }
            }
        }
    };
    private final ServiceConnection connection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            iIPrinterAidlInterface = IPrinterAidlInterface.Stub.asInterface(service);
            if (iIPrinterAidlInterface != null) {
                try {
                    iIPrinterAidlInterface.registerCallback(aidlCallback);
                    printPid = iIPrinterAidlInterface.getProcessId();
                    LLog.e("打印服务连接成功，服务进程ID：" + printPid);

                    if(currentPrinterType != null){
                        initPrinter(currentPrinterType);
                    }
                } catch (RemoteException e) {
                    LLog.e("打印服务连接失败：" + e.getMessage());
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            LLog.e("打印服务意外断开");
            if (iIPrinterAidlInterface != null) {
                try {
                    iIPrinterAidlInterface.unregisterCallback(aidlCallback);
                } catch (RemoteException e) {
                    LLog.e("注销回调失败：" + e.getMessage());
                }
            }
            iIPrinterAidlInterface = null;
            isServiceConnected = false;
        }
    };

    private Context context;// ApplicationContext，防止 Activity 内存泄漏
    private volatile boolean isServiceConnected = false;
    private Intent serviceIntent;

    /**
     * 启动并绑定打印服务。传入任意 Context 均可，内部自动取 applicationContext。
     */
    public synchronized void startService(Context ctx) {
        this.context = ctx.getApplicationContext();
        // 先解绑旧连接，防止重复绑定
        try {
            if (isServiceConnected) {
                LLog.i("解绑旧打印服务");
                this.context.unbindService(connection);
                isServiceConnected = false;
            }
            if (serviceIntent != null) {
                this.context.stopService(serviceIntent);
            }
        } catch (Exception e) {
            LLog.e("服务解绑失败：" + e.getMessage());
        }

        if (printPid != 0) {
            try {
                LLog.i("终止旧打印进程，PID：" + printPid);
                killProcessByPid(this.context, printPid);
                printPid = 0;
            } catch (Exception e) {
                LLog.e("终止进程失败：" + e.getMessage());
            }
        }

        serviceIntent = new Intent(this.context, PrintService.class);
        isServiceConnected = this.context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        LLog.i("绑定打印服务，结果：" + isServiceConnected);
    }

    /**
     * 主动停止打印服务并释放绑定。Activity/Application 销毁时调用。
     */
    public synchronized void stopService() {
        disposePrintCountdown();
        try {
            if (isServiceConnected && context != null) {
                context.unbindService(connection);
                isServiceConnected = false;
            }
            if (serviceIntent != null && context != null) {
                context.stopService(serviceIntent);
                serviceIntent = null;
            }
        } catch (Exception e) {
            LLog.e("stopService 失败：" + e.getMessage());
        }
        iIPrinterAidlInterface = null;
        LLog.i("打印服务已停止");
    }

    /**
     * 关闭服务
     * @param targetPid 目标进程ID
     */
    private void stopServiceByPid(Context context, int targetPid) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices != null && !runningServices.isEmpty()) {
            for (ActivityManager.RunningServiceInfo it : runningServices) {
                if (it.pid == targetPid) {
                    Intent stopIntent = new Intent();
                    stopIntent.setComponent(it.service);
                    if (context.stopService(stopIntent)) {
                        LLog.e("指定 PID 为 " + targetPid + " 的服务已停止");
                    } else {
                        LLog.e("停止服务失败，尝试终止进程");
                        killProcessByPid(context, targetPid);
                    }
                }
            }
        } else {
            LLog.e("未找到 PID 为 " + targetPid + " 的服务");
        }
    }

    /**
     * 终止进程
     * @param targetPid 目标进程ID
     */
    private void killProcessByPid(Context context, int targetPid) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
        if (runningAppProcesses != null && !runningAppProcesses.isEmpty()) {
            for (ActivityManager.RunningAppProcessInfo it : runningAppProcesses) {
                if (it.pid == targetPid) {
                    try {
                        activityManager.killBackgroundProcesses(it.processName);
                        LLog.e("已终止进程:" + it.processName);
                    } catch (SecurityException e) {
                        LLog.e("权限不足: " + e.getMessage());
                    }
                }
            }
        } else {
            LLog.e("未找到 PID 为 " + targetPid + " 的服务");
        }
    }

    /**
     * 初始化打印机
     * @param printerType 打印机类型
     */
    public void initPrinter(PrinterType printerType){
        currentPrinterType = printerType;
        LLog.i("发送打印机初始化指令，打印机类型：" + printerType);
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.INIT_PRINTER, printerType));
    }

    /**
     * 获取打印机状态
     * @param printerType 打印机类型
     */
    public void getPrinterStatus(PrinterType printerType){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.GET_PRINTER_STATE, printerType));
    }

    /**
     * 获取打印机剩余纸张数
     * @param printerType 打印机类型
     */
    public void getPrinterCount(PrinterType printerType){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.GET_PRINT_COUNT, printerType));
    }

    /**
     * 打印测试页
     * @param printerType 打印机类型
     */
    public void printImageTest(PrinterType printerType){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PRINT_IMAGE_TEST, printerType));
    }

    public void printUVTest(PrinterType printerType, int resId, int channel, float width, float height, float left, float top){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PRINT_IMAGE_TEST, printerType, resId, channel, width, height, left, top));
    }

    /**
     * 打印图片
     * @param printerType 打印机类型
     * @param imagePath 图片路径
     * @param printNum 打印数量
     * @param isCut 是否裁剪
     */
    public void printImage(PrinterType printerType, String imagePath, int printNum, boolean isCut) {
        lastPrintTime = System.currentTimeMillis();
        lastPrintEvent = new PrintEvent(PrintEvent.EventType.PRINT_IMAGE, printerType, imagePath, printNum, isCut);
        LLog.i("发送打印指令，图片路径：" + imagePath + "，打印数量：" + printNum + "，是否裁剪：" + isCut);
        needReprint = true;
        createPrintCountdown(printNum);
        sendPrintMsg(lastPrintEvent);
    }

    public void printImage(PrinterType printerType, String imagePath, int channel, float width, float height, float left, float top){
        lastPrintTime = System.currentTimeMillis();
        lastPrintEvent = new PrintEvent(PrintEvent.EventType.PRINT_IMAGE, printerType, imagePath, channel, width, height, left, top);
        LLog.i("发送UV打印指令，图片路径：" + imagePath + ",通道：" + channel + ",宽：" + width + ",高:" + height + ",左边距：" + left + ",上边距:" + top);
        needReprint = true;
        createPrintCountdown(1);
        sendPrintMsg(lastPrintEvent);
    }

    private Disposable countdownDisposable;

    /**
     * 创建打印超时倒计时
     * @param printNum 张数
     */
    private void createPrintCountdown(int printNum){
        disposePrintCountdown();
        int totalSecond = printNum * 60 * 1000;
        LLog.i("创建打印失败倒计时，时长：" + totalSecond + "秒");
        if(currentPrinterType == PrinterType.UV){
            totalSecond = printNum * 90 * 1000;
        }
        countdownDisposable = RxTask.postDelayed(() -> {
            LLog.e("打印机超时没有返回失败的结果，停止打印");
            disposePrintCountdown();
            // 停止当前服务，重新打开
            repeatPrint();
        }, totalSecond);
    }

    /**
     * 关闭打印超时倒计时
     */
    private void disposePrintCountdown(){
        if(countdownDisposable != null && !countdownDisposable.isDisposed()){
            countdownDisposable.dispose();
            countdownDisposable = null;
        }
    }

    private volatile long lastPrintTime = 0;// 上次打印时间
    private volatile PrintEvent lastPrintEvent = null;// 上次打印事件
    private volatile boolean needReprint = false;// 是否需要重打印
    /** 防止短时间内重复触发重启的标记 */
    private volatile boolean isRestarting = false;
    /**
     * 接收打印服务回调数据，在 Binder 线程执行。
     * 打印失败且在 15 秒快速失败窗口内 → 自动重启服务后重试（仅重试一次）。
     */
    private void getPrinterServiceMsg(ExternalPrintEvent event) {
        if (event.getEvent() == ExternalPrintEvent.EventType.PRINT_IMAGE) {
            disposePrintCountdown();
            LLog.i("收到打印结果，状态：" + event.isStatus() + "，信息：" + event.getMsg());
            long elapsed = System.currentTimeMillis() - lastPrintTime;
            if (lastPrintEvent != null && elapsed < 15000 && !event.isStatus() && !isRestarting) {
                LLog.e("打印快速失败（" + elapsed + "ms），重启服务后重试");
                isRestarting = true;
                needReprint = false; // 防止 repeatPrint 再次触发
                if (context != null) {
                    startService(context);
                    RxTask.postDelayed(() -> {
                        isRestarting = false;
                        LLog.i("重新发送打印指令");
                        sendPrintMsg(lastPrintEvent);
                    }, 5000);
                }
                return;
            } else {
                needReprint = false;
            }
        } else if (event.getEvent() == ExternalPrintEvent.EventType.RESTART_SERVICE) {
            repeatPrint();
            return;
        }
        EventBus.getDefault().post(event);
    }

    /**
     * 打印超时处理：切换 OTG 模式以复位 USB 打印机，然后重新发送打印指令。
     */
    private void repeatPrint() {
        if (isRestarting) {
            LLog.i("已在重启中，跳过重复 repeatPrint");
            return;
        }
        isRestarting = true;
        LLog.i("打印超时，切换 OTG 复位后重试");
        try {
            ZtlManager.GetInstance().setUSBtoPC(true);
        } catch (Exception e) {
            LLog.e("OTG 开启失败：" + e.getMessage());
        }
        RxTask.postDelayed(() -> {
            try {
                ZtlManager.GetInstance().setUSBtoPC(false);
            } catch (Exception e) {
                LLog.e("OTG 关闭失败：" + e.getMessage());
            }
            RxTask.postDelayed(() -> {
                isRestarting = false;
                if (needReprint && lastPrintEvent != null) {
                    LLog.i("超时重打：重新发送打印指令");
                    needReprint = false;
                    sendPrintMsg(lastPrintEvent);
                } else {
                    LLog.i("无需重打");
                }
            }, 5000);
        }, 5000);
    }

    /**
     * 发送指令到服务
     */
    public void sendPrintMsg(PrintEvent printEvent) {
        try {
            if (iIPrinterAidlInterface != null){
                Bundle bundle = new Bundle();
                bundle.putSerializable("printEvent", printEvent);
                iIPrinterAidlInterface.sendData(bundle);
            }else{
                LLog.e("打印服务未连接，无法发送打印指令");
            }
        } catch (Exception e) {
            LLog.e("发送打印指令失败：" + e.getMessage());
        }
    }

    /**
     * 设置DNP打印机参数
     * @param printerType 打印机类型
     * @param offsetX 打印偏移X
     * @param color 颜色参数
     */
    public void setDnpPrinterParameter(PrinterType printerType, int offsetX, int color){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PARAMETER_SETTING, printerType, offsetX, color));
    }

    /**
     * 设置HITI打印机参数
     */
    public void setHitiPrinterParameter(){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PARAMETER_SETTING, PrinterType.HITI));
    }

    /**
     * 设置UV打印机清洗喷头
     */
    public void setUVPrintClean(){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PARAMETER_SETTING, PrinterType.UV, 9));
    }

    public void setUVPrintCheck(){
        sendPrintMsg(new PrintEvent(PrintEvent.EventType.PARAMETER_SETTING, PrinterType.UV, 10));
    }

}
