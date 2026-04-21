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
 * 打印机工厂类
 */
public class PrintFactory {
    /*单例模式*/
    private PrintFactory() {
    }
    private static PrintFactory instance;

    public static PrintFactory getInstance() {
        if (instance == null) {
            instance = new PrintFactory();
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
            if (iIPrinterAidlInterface != null) {
                try {
                    iIPrinterAidlInterface.unregisterCallback(aidlCallback);
                } catch (RemoteException e) {
                    LLog.e("打印服务断开连接，注销回调失败：" + e.getMessage());
                }
            }
            iIPrinterAidlInterface = null;
        }
    };

    private Context context;// 上下文
    private boolean isServiceConnected = false;
    private Intent serviceIntent;
    /**
     * 启动服务
     */
    public void startService(Context context){
        this.context = context;
        // 解绑旧服务防止重复绑定
        try{
            LLog.i("尝试解绑旧打印服务");
            // 判断服务是否被注册
            if(isServiceConnected) {
                context.unbindService(connection);
            }
            if(serviceIntent != null){
                context.stopService(serviceIntent);
            }
        }catch (Exception e){
            LLog.e("服务解绑失败：" + e.getMessage());
        }

        if (printPid != 0) {
            try {
                LLog.i("尝试停止旧打印服务，PID：" + printPid);
//                stopServiceByPid(context, printPid);
                killProcessByPid(context, printPid);
                printPid = 0;
            } catch (Exception e) {
                LLog.e("DNPManage initPrint" + ": " + e.getMessage());
            }
        }
        serviceIntent = new Intent(context, PrintService.class);
        isServiceConnected = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
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

    private long lastPrintTime = 0;// 上次打印时间
    private PrintEvent lastPrintEvent = null;// 上次打印事件
    private boolean needReprint = false;// 是否需要重打印
    /**
     * 接收打印机服务返回的数据
     */
    private void getPrinterServiceMsg(ExternalPrintEvent event){
        if(event.getEvent() == ExternalPrintEvent.EventType.PRINT_IMAGE){
            //判断当前返回结果和开始打印时间间隔少于5秒，并且失败了，则重启服务，然后再次打印
            disposePrintCountdown();
            LLog.i("收到打印结果返回，状态：" + event.isStatus() + "，信息：" + event.getMsg());
            long currentTime = System.currentTimeMillis();
            if(lastPrintEvent != null && (currentTime - lastPrintTime) < 15000) {
                LLog.i("打印结果返回时间：" + (currentTime - lastPrintTime) + "ms");
                if (!event.isStatus()) {
                    LLog.e("打印失败，尝试重启打印服务并重新打印");
                    //重启服务
                    startService(context);
                    RxTask.postDelayed(()->{
                        //重新打印
                        LLog.i("重新发送打印指令");
                        sendPrintMsg(lastPrintEvent);
                    }, 5000);
                    return;
                }
            }else{
                needReprint = false;
            }
        } else if(event.getEvent() == ExternalPrintEvent.EventType.RESTART_SERVICE){
            repeatPrint();
            return;
        }
        EventBus.getDefault().post(event);
    }

    private void repeatPrint(){
        LLog.i("超时没有返回结果，打印服务请求重启服务");
//        startService(context);
        ZtlManager.GetInstance().setUSBtoPC(true);// 打开otg调试
        RxTask.postDelayed(()->{
            ZtlManager.GetInstance().setUSBtoPC(false);// 关闭otg调试
            RxTask.postDelayed(()->{
                //重新打印
                if(needReprint){
                    LLog.i("重新发送打印指令");
                    needReprint = false;
                    sendPrintMsg(lastPrintEvent);
                }else{
                    LLog.i("没有需要重打印的指令");
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
