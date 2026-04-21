//package com.dawn.printers.xitiecheng;
//
//import android.content.Context;
//import android.util.Log;
//
//import com.dawn.printers.IPrinterCallbackListener;
//import com.dawn.printers.PrinterManage;
//import com.dawn.util_fun.RxTask;
//import com.dawn.util_fun.LLog;
//import com.google.gson.Gson;
//import com.printer.sdk.PrintMsg;
//
//public class C617Manage extends PrinterManage {
//
//    private final XTCRSHPrintUtil xtcrshPrintUtil;
//    private String type = "C617";
//    private int initCount = 0;
//
//    public C617Manage(Context context, IPrinterCallbackListener mPrinterCallbackListener) {
//        super(context, mPrinterCallbackListener);
//        xtcrshPrintUtil = XTCRSHPrintUtil.singleInstance(context);
//    }
//
//    public void setType(String type) {
//        this.type = type;
//    }
//
//    @Override
//    public void initPrinter() {
//        xtcrshPrintUtil.initPrint(type, new OnInitPrint() {
//            @Override
//            public void onInitSuccess() {
//                Log.i("xhk", "启动成功");
//                mPrinterCallbackListener.onInit();
//            }
//
//            @Override
//            public void onInitFail(PrintMsg info) {
//                Log.i("xhk", "启动失败" + info.toString());
//                RxTask.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        initCount++;
//                        if (initCount < 10)
//                            initPrinter();
//                    }
//                }, 3000);
//            }
//        });
//    }
//
//    @Override
//    public void stop() {
//
//    }
//
//    @Override
//    public void queryState() {
//
//    }
//
//    @Override
//    public void startPrint(String imagePath, int printNum, boolean isCut, int printMargin, int color) {
//        xtcrshPrintUtil.printImage(imagePath, printNum, isCut, new OnPrintResult() {
//            @Override
//            public void onSuccess() {
//                Log.i("xhk", "打印成功了");
//                mPrinterCallbackListener.onQueryState(true, "打印成功了");
//            }
//
//            @Override
//            public void onFail(PrintMsg info) {
//                Log.i("xhk", "打印失败:" + info);
//                mPrinterCallbackListener.onQueryState(false, new Gson().toJson(info));
//            }
//        });
//    }
//
//    @Override
//    public void printTest() {
//
//    }
//
//    @Override
//    public void queryPrintCount() {
//        xtcrshPrintUtil.getPrintNumber(new OnPrintNumber() {
//            @Override
//            public void onPrintNumber(int printNumber) {
//                LLog.e("xhk","printNumber:"+printNumber);
//                mPrinterCallbackListener.onQueryPrintCount(true, printNumber, "");
//            }
//        });
//    }
//}
