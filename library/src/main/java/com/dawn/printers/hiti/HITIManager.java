package com.dawn.printers.hiti;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.dawn.printers.IPrinterCallbackListener;
import com.dawn.printers.PrinterManage;
import com.dawn.printers.PrinterType;
import com.dawn.printers.internal.RxTask;
import com.dawn.util_fun.LLog;

import java.io.File;

import io.reactivex.rxjava3.disposables.Disposable;

public class HITIManager extends PrinterManage {
    private PrintUtil mPrintUtil;// 打印机工具类
    private final static int DEFAULT_PRINT_TIME = 20 * 1000;//默认打印时间，单位毫秒
    private final static String STATUS_SUCCESS = "success";//打印成功状态标识
    private int currentStatus;// 当前打印机状态,0 空闲，1 打印中

    public HITIManager(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
        super(context, mPrinterCallbackListener);
    }

    @Override
    public void initPrinter(PrinterType printerType) {
        if (mPrintUtil == null) {
            mPrintUtil = new PrintUtil(context);
        }
        mPrintUtil.start(context);
        /*查询打印机状态*/
        RxTask.postDelayed(() -> {
            LLog.i("查询打印机初始化状态");
            // 注意：getPrintStatus 内部会走 JNI/USB，同步调用在主线程上可能卡住，导致后续日志不打印
            RxTask.runAsync(() -> {
                try {
                    return mPrintUtil.getPrintStatus();
                } catch (Throwable t) {
                    return "getPrintStatus exception: " + t.getMessage();
                }
            }, new RxTask.UiTask<String>() {
                @Override
                public void onSuccess(String status) {
                    LLog.i("打印机初始化状态：" + status);
                    if (STATUS_SUCCESS.equals(status)) {
                        LLog.i("打印机初始化成功");
                        mPrinterCallbackListener.initStatus(PrinterType.HITI, true, "打印机初始化成功");
                        int printNum = mPrintUtil.getPrintNums();
                        mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, printNum, "");
                    } else {
                        LLog.e("打印机初始化失败");
                        mPrinterCallbackListener.initStatus(PrinterType.HITI, false, status);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    String msg = throwable == null ? "unknown" : throwable.getMessage();
                    LLog.e("getPrintStatus异常：" + msg);
                    mPrinterCallbackListener.initStatus(PrinterType.HITI, false, msg);
                }
            });
        }, 3000);
    }

    @Override
    public void stop() {
        if (mPrintUtil != null)
            mPrintUtil.stop();
    }

    @Override
    public void getStatus() {
        if (mPrintUtil == null) {
            LLog.e("打印机未初始化，无法查询状态");
            mPrinterCallbackListener.initStatus(PrinterType.HITI, false, "打印机未初始化");
            return;
        }
        RxTask.runAsync(() -> {
            try {
                String status = mPrintUtil.getPrintStatus();
                LLog.i("HITI 状态查询：" + status);
                mPrinterCallbackListener.initStatus(PrinterType.HITI, STATUS_SUCCESS.equals(status), status);
            } catch (Throwable t) {
                LLog.e("HITI 状态查询异常：" + t.getMessage());
                mPrinterCallbackListener.initStatus(PrinterType.HITI, false, t.getMessage());
            }
        });
    }

    @Override
    public void startPrint(String imagePath, int printNum, boolean isCut) {
        if(mPrintUtil == null){
            LLog.e("打印机未初始化，无法打印");
            return;
        }
        if(currentStatus == 1)
            return;
        currentStatus = 1;
        final short[] paperType = new short[1];
        LLog.i("开始打印图片：" + imagePath + "，打印份数：" + printNum + "，是否切纸：" + isCut);
        RxTask.runAsync(() -> {
            if (isCut) {
                paperType[0] = PrintConstant.PaperType_cut;
            } else {
                paperType[0] = PrintConstant.PaperType;
            }
            if (TextUtils.isEmpty(imagePath) || !new File(imagePath).exists()) {
                LLog.e("当前打印的图片为空或不存在");
                currentStatus = 0;
                mPrinterCallbackListener.getPrintResult(PrinterType.HITI, false, "当前打印的图片为空或不存在");
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                LLog.e("打印的图片Bitmap解析失败");
                currentStatus = 0;
                mPrinterCallbackListener.getPrintResult(PrinterType.HITI, false, "打印的图片Bitmap解析失败");
                return;
            }
            boolean result = mPrintUtil.PrintImg(context, PrintConstant.PRINTMODE, PrintConstant.MATTE, paperType[0], (short) printNum, bitmap);
            if (result) {
                LLog.i("图片传输成功，开始打印");
                cycleGetPrintResult(printNum);
            }else{
                LLog.e("图片传输失败，打印终止");
                currentStatus = 0;
                mPrinterCallbackListener.getPrintResult(PrinterType.HITI, false, "图片传输失败，打印终止");
            }
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        });
    }

    @Override
    public void printTest() {
        if(mPrintUtil == null){
            LLog.e("打印机未初始化，无法打印");
            return;
        }
        mPrintUtil.PrintTest(context);
    }

    @Override
    public void getPrintCount() {
        if(mPrintUtil == null){
            LLog.e("打印机未初始化，无法查询打印数量");
            mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, -1, "打印机未初始化");
            return;
        }
        RxTask.runAsync(() -> {
            int printNum = mPrintUtil.getPrintNums();
            if(printNum == -1){
                LLog.e("获取打印机剩余纸张数失败,获取打印机状态");
                String status = mPrintUtil.getPrintStatus();
                if(STATUS_SUCCESS.equals(status)){
                    mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, printNum, "");
                }else{
                    // 再次连接一次
                    initPrinter(PrinterType.HITI);
                    printNum = mPrintUtil.getPrintNums();
                    if(printNum == -1){
                        status = mPrintUtil.getPrintStatus();
                        mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, printNum, status);
                    }else{
                        LLog.i("获取打印机剩余纸张数成功，剩余纸张数：" + printNum);
                        mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, printNum, "");
                    }
                }
            }else{
                LLog.i("获取打印机剩余纸张数成功，剩余纸张数：" + printNum);
                mPrinterCallbackListener.getPrinterCount(PrinterType.HITI, printNum, "");
            }
        });
    }

    private Disposable cycleGetStatus;// 循环查询打印结果的任务
    /**
     * 循环获取打印结果
     */
    private void cycleGetPrintResult(int printNum){
        RxTask.postDelayed(()->{
            // 开始循环查询状态,直到返回成功或者失败为止
            closeDisposable();
            cycleGetStatus = RxTask.countdown(30, 5, new RxTask.CountdownTask() {
                @Override
                public void onNext(int remainingSeconds) {
                    String status = mPrintUtil.getPrintStatus();
                    LLog.i("循环获取打印结果，当前状态：" + status);
                    if(STATUS_SUCCESS.equals(status)){
                        LLog.e("打印成功");
                        currentStatus = 0;
                        mPrinterCallbackListener.getPrintResult(PrinterType.HITI, true, "打印成功");
                        closeDisposable();
                    }
                }

                @Override
                public void onComplete() {
                    String status = mPrintUtil.getPrintStatus();
                    if(STATUS_SUCCESS.equals(status)){
                        LLog.e("打印成功");
                        currentStatus = 0;
                        mPrinterCallbackListener.getPrintResult(PrinterType.HITI, true, "打印成功");
                    }else{
                        currentStatus = 0;
                        mPrinterCallbackListener.getPrintResult(PrinterType.HITI, false, "打印失败，状态：" + status);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    LLog.e("循环获取打印结果异常：" + throwable.getMessage());
                    currentStatus = 0;
                    mPrinterCallbackListener.getPrintResult(PrinterType.HITI, false, "打印异常：" + throwable.getMessage());
                }
            });
        }, DEFAULT_PRINT_TIME * printNum);
    }

    private void closeDisposable(){
        if(cycleGetStatus != null && !cycleGetStatus.isDisposed()){
            cycleGetStatus.dispose();
            cycleGetStatus = null;
        }
    }

    /**
     * 色带校验
     */
    public void printRibbonCalibration() {
        if(mPrintUtil == null){
            LLog.e("打印机未初始化，无法校准色带");
            return;
        }
        LLog.e("打印机色带校准开始");
        String result = mPrintUtil.getPrintRibbonCalibration();
        LLog.i("打印机色带校准结果：" + result);
    }

    /**
     * 打印机色带信息
     */
    public void printRibbonInfo() {
        if(mPrintUtil == null){
            LLog.e("打印机未初始化，无法获取色带信息");
            return;
        }
        LLog.e("打印机色带信息查询开始");
        String result = mPrintUtil.getPrintRibbonInfo();
        LLog.i("打印机色带信息结果：" + result);
    }
}
