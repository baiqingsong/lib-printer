package com.dawn.printers.hiti;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import com.hiti.usb.jni.JniData;
import com.hiti.usb.printer.PrinterJob;
import com.hiti.usb.printer.PrinterStatus;
import com.hiti.usb.service.ErrorCode;
import com.hiti.usb.service.ServiceConnector;
import com.hiti.usb.utility.FileUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ScheduledExecutorService;


/**
 * 打印工具
 */
class PrintUtil {
    private static final int PAPERTYPE = 2;
    private static final short MATTE = 0;//0光滑，1模糊
    private static final short PRINTCOUNT = 1;
    private static final short PRINTMODE = 0;//0标准，1精细

    public ServiceConnector serviceConnector;//打印服务连接类
    public PrinterOperation operation;//打印机操作类

    private static PrintUtil printUtil;
    public PrintUtil(Context context){
        InitialPrintValue(context);
    }


    ScheduledExecutorService exec3;
    /**
     * 初始化，开启服务
     */
    public void start(Context context){
        if (serviceConnector != null) {//开启打印服务
            ErrorCode errorCode = serviceConnector.StartService();
            if(errorCode != null) {// && errorCode != ErrorCode.ERR_CODE_SUCCESS
                StringBuilder bu = new StringBuilder("\n>>>").append("start service")
                        .append(": err<0x").append(Integer.toHexString(errorCode.value)).append(" ")
                        .append(errorCode.description).append(">");
                Log.e("PrintUtil","print service error " + bu.toString());
            }else{
//                Log.e("PrintUtil","print service error " + errorCode + " " + ErrorCode.ERR_CODE_SUCCESS + " " + serviceConnector);
//                StringBuilder bu = new StringBuilder("\n>>>").append("start service")
//                        .append(": err<0x").append(Integer.toHexString(errorCode.value)).append(" ")
//                        .append(errorCode.description).append(">");
//
//                Log.e("PrintUtil","print service error " + bu.toString());
            }
        }
    }

    /**
     * 设置参数
     *
     * @param printMode //打印模式，标准或精细  0标准，1精细
     * @param matte     //打印覆膜  0光滑，1模糊
     */
    public void setParam(short printMode, short matte) {
        if (operation == null)
            return;
        if (printMode == 0 || printMode == 1)
            operation.PRINTMODE = printMode;
        else
            operation.PRINTMODE = PRINTMODE;
        if (matte == 0 || matte == 1)
            operation.MATTE = matte;
        else
            operation.MATTE = MATTE;
    }

    /**
     * 打印照片
     * @param printMode 打印模式，标准或精细
     * @param matte 光面或磨砂面
     * @param paperType 打印类型，4,6格
     * @param printCount 打印张数
     */
    public boolean PrintImg(Context context, short printMode, short matte, short paperType, short printCount, Bitmap photoPath){
        if(printCount > 0 && printCount < 10)
            operation.PRINTCOUNT = printCount;
        else
            operation.PRINTCOUNT = PRINTCOUNT;
        if(printMode == 0 || printMode == 1)
            operation.PRINTMODE = printMode;
        else
            operation.PRINTMODE = PRINTMODE;
        if(matte == 0 || matte == 1)
            operation.MATTE = matte;
        else
            operation.MATTE = MATTE;
        if(paperType == 2 || paperType == 5)
            operation.PaperType = paperType;
        else
            operation.PaperType = PAPERTYPE;
        if(TextUtils.isEmpty(m_strTablesRoot)){
            InitialPrintValue(context);
        }
        operation.m_strTablesRoot = m_strTablesRoot;
        PrinterJob printerJob = operation.print(photoPath);
        if (printerJob != null && printerJob.errCode != null && printerJob.errCode.value == 0) {//打印成功
            PrinterJob ribbonInfoJob = operation.getRibbonInfo();
            if (ribbonInfoJob != null && ribbonInfoJob.errCode != null && ribbonInfoJob.errCode.value == 0) {//获取打印机张数成功
                if (ribbonInfoJob.retData instanceof JniData.IntArray) {
                    if (((JniData.IntArray) ribbonInfoJob.retData).getSize() > 1) {
                        return true;//打印成功
                    }
                }
            }
        }
        return false;//打印失败
    }

    /**
     * 测试打印
     */
    public void PrintTest(Context context){
        operation.PRINTCOUNT = 1;
        operation.MATTE = 0;
        operation.PRINTMODE = 0;
        operation.PaperType = 2;
        if(TextUtils.isEmpty(m_strTablesRoot)){
            InitialPrintValue(context);
        }
        operation.m_strTablesRoot = m_strTablesRoot;
        operation.print("pic1844x1240");
//        operation.print("test");
    }

    /**
     * 获取剩余纸张数量
     */
    public int getPrintNums(){
        if(operation == null)
            return -1;
        PrinterJob ribbonInfoJob = operation.getRibbonInfo();
        if (ribbonInfoJob != null && ribbonInfoJob.errCode != null && ribbonInfoJob.errCode.value == 0) {//获取打印机张数成功

            if (ribbonInfoJob.retData instanceof JniData.IntArray) {
                if (((JniData.IntArray) ribbonInfoJob.retData).getSize() > 1) {
                    //打印机张数
                    return ((JniData.IntArray) ribbonInfoJob.retData).get(1);
                }
            }
        }
        return -1;
    }

    public String getPrintStatus(){
        if(operation == null)
            return "init fail";
        PrinterJob printerJob = operation.getPrinterStatus();
        if (printerJob != null && printerJob.errCode != null && printerJob.errCode.value == 0) {//获取打印机状态成功
            StringBuilder bu = new StringBuilder();
            if (printerJob.retData instanceof PrinterStatus) {
                PrinterStatus status = ((PrinterStatus)printerJob.retData);
                bu.append("\nStatus: 0x").append(Integer.toHexString(status.statusValue)).append(" ")
                        .append(status.statusDescription);
                Log.e("PrintUtil","get printer status " + bu.toString());
                if(status.statusValue == 0){
                    return "success";
                }else{
                    return "" + status.statusDescription;
                }
            }else{
                return "解析异常";
            }
        }else{
            return "无打印机";
        }
    }

    public String getPrintRibbonCalibration(){
        if(operation == null)
            return "init fail";
        PrinterJob printerJob = operation.calibrateRibbonLED();
        if (printerJob != null && printerJob.errCode != null && printerJob.errCode.value == 0) {//获取打印机状态成功
            StringBuilder sb = new StringBuilder();
            if (printerJob != null && printerJob.errCode != null && printerJob.errCode.value == 0) {//获取打印机张数成功
                if (printerJob.retData instanceof JniData.IntArray) {
                    for(int i = 0; i< ((JniData.IntArray)printerJob.retData).getSize(); i++) {
                        sb.append("\ndata[").append(i).append("]: ")
                                .append(((JniData.IntArray)printerJob.retData).get(i));
                    }
                }
                return sb.toString();
            }else{
                return "print result type error";
            }
        }else{
            return "print code error";
        }
    }

    public String getPrintRibbonInfo(){
        if(operation == null)
            return "init fail";
        PrinterJob ribbonInfoJob = operation.getRibbonInfo();
        StringBuilder sb = new StringBuilder();
        if (ribbonInfoJob != null && ribbonInfoJob.errCode != null && ribbonInfoJob.errCode.value == 0) {//获取打印机张数成功
            if (ribbonInfoJob.retData instanceof JniData.IntArray) {
                for(int i = 0; i< ((JniData.IntArray)ribbonInfoJob.retData).getSize(); i++) {
                    sb.append("\ndata[").append(i).append("]: ")
                            .append(((JniData.IntArray)ribbonInfoJob.retData).get(i));
                }
            }
            return sb.toString();
        }
        return "print code error";
    }

    /**
     * 停止，注销
     */
    public void stop(){
        if(serviceConnector != null){
//            serviceConnector.unregister();
            serviceConnector.StopService();
        }
    }

    public String m_fwversion, m_fwpath, m_fwfolderpath, m_fwBootpath, m_fwKernelpath;
    public String m_strTablesCopyRoot = "";
    public String m_strTablesRoot = "";
    //打印机相关配置
    public void InitialPrintValue(Context context) {
//        Log.i("","init print value " + serviceConnector + "   " + operation);
        //Create and Copy color bin file from asset folder
        m_strTablesCopyRoot = context.getExternalFilesDir(null).getAbsolutePath();
        m_strTablesRoot = context.getExternalFilesDir(null).getAbsolutePath() + "/Tables";
        if (!FileUtility.FileExist(m_strTablesRoot)) {
            FileUtility.CreateFolder(m_strTablesRoot);
        }
        String strLogDir = context.getExternalFilesDir(null).getAbsolutePath()+"/Logs";
        if(!FileUtility.FileExist(strLogDir)) {
            FileUtility.CreateFolder(strLogDir);
        }

        copyFileOrDir(context, "Tables");

        InitialValueFW(context);
        InitialP525FW(context);

        serviceConnector = ServiceConnector.register(context, null);
        operation = new PrinterOperation(context, serviceConnector);

        //init value
        operation.PaperType = PAPERTYPE;//Set printout size. 2:4x6, 3:5x7, 4:6x8
        operation.MATTE = MATTE;//1:matte, 0:notmatte
        operation.PRINTCOUNT = PRINTCOUNT;//Want to print count
        operation.PRINTMODE = PRINTMODE;//Only for P232W, default 0. 1:fine mode(HOD), 0:standard mode
        operation.m_strTablesRoot = m_strTablesRoot;

    }

    private void copyFileOrDir(Context context, String path) {
        AssetManager assetManager = context.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list(path);
            if (assets.length == 0) {
                copyFile(context, path);
            } else {
                String fullPath = "/data/data/" + context.getPackageName() + "/" + path;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (int i = 0; i < assets.length; ++i) {
                    copyFileOrDir(context, path + "/" + assets[i]);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void copyFile(Context context, String filename) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(filename);
            String newFileName = m_strTablesCopyRoot + "/" + filename;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void InitialValueFW(Context context)
    {
        //FW version and path
        m_fwversion = "1.16.0.Z";
        m_fwfolderpath = context.getExternalFilesDir(null).getAbsolutePath()+"/HiTi_FW";
        m_fwpath = m_fwfolderpath + "/ROM_ALL_p520l.bin";
        if(!FileUtility.FileExist(m_fwfolderpath))
        {
            FileUtility.CreateFolder(m_fwfolderpath);
        }
        //Copy asset fw to absolutepath
        AssetManager assetManager = context.getAssets();
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open("HiTi_FW/ROM_ALL_p520l.bin");
            out = new FileOutputStream(m_fwpath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    private void InitialP525FW(Context context)
    {
        //FW version and path
        m_fwversion = "1.02.0.m";
        m_fwfolderpath = context.getExternalFilesDir(null).getAbsolutePath()+"/HiTiP525N_FW";
        m_fwpath = m_fwfolderpath + "/rootfs.brn";
        m_fwBootpath = m_fwfolderpath + "/boot.brn";
        m_fwKernelpath = m_fwfolderpath + "/kernel.brn";
        if(!FileUtility.FileExist(m_fwpath))
        {
            m_fwpath = null;
            m_fwBootpath = null;
            m_fwKernelpath = null;
        }
        if(!FileUtility.FileExist(m_fwBootpath))
        {
            m_fwBootpath = null;
        }
        if(!FileUtility.FileExist(m_fwKernelpath))
        {
            m_fwKernelpath = null;
        }
    }

}
