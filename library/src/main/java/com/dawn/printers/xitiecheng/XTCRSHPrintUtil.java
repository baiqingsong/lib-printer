package com.dawn.printers.xitiecheng;

import android.content.Context;
import android.util.Log;

import com.printer.sdk.OnAddOrderListen;
import com.printer.sdk.OnGetInfoListen;
import com.printer.sdk.OnStartPrnListen;
import com.printer.sdk.OnStartSvrListen;
import com.printer.sdk.PrintManage;
import com.printer.sdk.PrintMsg;
import com.printer.sdk.PrintType;
import com.printer.sdk.PrintUserOrder;
import com.printer.sdk.PrintUserTask;

import java.util.ArrayList;
import java.util.List;

/**
 * XTCRSH（西铁城热升华打印机）打印机工具类
 */
public class XTCRSHPrintUtil {

    private static XTCRSHPrintUtil printUtil;
    private static PrintManage mPrintManage;

    private String printType;

    public static synchronized XTCRSHPrintUtil singleInstance(Context context){
        if(printUtil == null)
            printUtil = new XTCRSHPrintUtil(context);
        return printUtil;
    }

    private XTCRSHPrintUtil(Context context) {
        this.mPrintManage = PrintManage.instance(context);
    }

    //初始化打印机
    public void initPrint(String printType,OnInitPrint onInitPrint){
        this.printType = printType;
        //关闭模拟打印
        mPrintManage.SetPrinterDebug(false);
        //6x4  6x8
        mPrintManage.SetPrinterTypeAndMedia(printType, "6x4");//打印机类型 打印尺寸
        mPrintManage.StartSvr(new OnStartSvrListen() {
            @Override
            public int Result(PrintMsg info) {
                switch (info.getRet()){
                    case PrintType.MSG_OK://启动服务正常，初始化成功
                        Log.i("xhk","启动服务正常，初始化成功");
                        startPrintService(new OnStartPrint() {
                            @Override
                            public void onStarSuccess() {
                                onInitPrint.onInitSuccess();
                            }

                            @Override
                            public void onStarFail(PrintMsg info) {
                                onInitPrint.onInitFail(info);
                            }
                        });
                        break;
                    case PrintType.MSG_ER:	//启动服务错误
                        Log.i("xhk","启动服务错误");
                        onInitPrint.onInitFail(info);
                        break;
                    default://其他DEBUG信息
                        Log.i("xhk","其他DEBUG信息");
                        onInitPrint.onInitFail(info);
                        break;
                }
                return 0;
            }
        });
    }

    //启动打印服务
    private void startPrintService(OnStartPrint onStartPrint){
        mPrintManage.StartPrn(new OnStartPrnListen() {
            @Override
            public int Result(PrintMsg info) {
                Log.i("xhk", "打印服务启动：" + info.toString());
                switch (info.getRet()) {
                    case PrintType.MSG_ER:    //启动打印服务错误
                        onStartPrint.onStarFail(info);
                        break;
                    case PrintType.MSG_OK:    //启动打印服务正常
                        Log.i("xhk", "启动打印服务正常>>>"+info.getMsg()+","+ info.getRet());
                        break;
                    case PrintType.MSG_PRN:	//启动打印
                        Log.i("xhk", "启动打印>>>"+info.getMsg()+","+ info.getRet());
                        onStartPrint.onStarSuccess();
                        break;
                    default:                //其他DEBUG信息
                        onStartPrint.onStarFail(info);
                        break;
                }

                return 0;
            }
        });
    }

    //获取剩余打印机纸张数量
    public void getPrintNumber(OnPrintNumber onPrintNumber) {
        Log.i("xhk", "getPrintNumber：");
        mPrintManage.GetPrnInfo(new OnGetInfoListen() {
            @Override
            public int Result(PrintMsg info) {
                Log.i("xhk", "getPrintNumber：" + info.toString());
                switch (info.getRet()){
                    case PrintType.MSG_OK://启动服务正常，初始化成功
                        if (info.getPrintStus()!=null){
                            //MQTY0011??????  纸张剩余数量（根据色带判断的）
                            int printNumber = NumberUtil.extractNumbers(info.getPrintStus().getPrintRemainQuantity());
                            onPrintNumber.onPrintNumber(printNumber);
                        }
                        break;
                    case PrintType.MSG_ER://启动服务失败
                        if (info.getMsg().equals("获取状态失败")){
                            onPrintNumber.onPrintNumber(-1);
                        }
                        break;
                    default:
                        break;
                }
                return 0;
            }
        });
    }

    /**
     *  打印相片
     * @param imagePath   相片路径
     * @param num         打印数量
     * @param isCut       是否切割
     * @param onPrintResult    结果回调
     */
    public void printImage(String imagePath,int num,boolean isCut ,OnPrintResult onPrintResult){
        List<PrintUserTask> task = new ArrayList<PrintUserTask>();
        PrintUserTask mPrintUserTask = new PrintUserTask(imagePath, num);
        mPrintUserTask.setTaskid(0);
        task.add(mPrintUserTask);
        PrintUserOrder mPrintUserOrder = new PrintUserOrder();
        mPrintUserOrder.setTask(task);
        mPrintUserOrder.setPrns("ONE");
        mPrintUserOrder.setBdpi("300x600");
        mPrintUserOrder.setMode("FIT");
        mPrintUserOrder.setMatte(false);
        mPrintUserOrder.setOrderid(System.currentTimeMillis());
        if (isCut){
            mPrintUserOrder.setCutType("2寸裁切");
        }else{
            mPrintUserOrder.setCutType("正常裁切");
        }
        Log.i("mPrintUserOrder:",mPrintUserOrder.toString());
        mPrintManage.AddOrder(mPrintUserOrder, new OnAddOrderListen() {
            @Override
            public int Result(PrintMsg info) {
                Log.i("xhk","打印结果："+info.toString());
                switch(info.getRet()) {
                    case PrintType.MSG_PIC:	//启动打印错误
                        Log.i("xhk","启动打印错误："+info.toString());
                        break;
                    case PrintType.MSG_OFT:	//加载进度
                        break;
                    case PrintType.MSG_ER:
                        if (!info.getMsg().equals("false")){
                            Log.i("xhk","打印错误："+info.toString());
                            onPrintResult.onFail(info);
                        }
                        break;
                    case PrintType.MSG_OK:
                        Log.i("xhk","完成打印："+info.toString());
                        onPrintResult.onSuccess();
                        break;
                }
                return 0;
            }
        });
    }
}

