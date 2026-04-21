package com.dawn.printers.icod;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.szsicod.print.escpos.PrinterAPI;
import com.szsicod.print.io.USBAPI;
import com.szsicod.print.utils.BitmapUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class IcodPrintUtil {
    private Context mContext;
    //单例模式
    private static IcodPrintUtil instance;

    private PrinterAPI printerAPI;

    public IcodPrintUtil(Context context) {
        this.mContext = context;
        printerAPI = PrinterAPI.getInstance();
        init();
    }

    public void init() {
        printerAPI.init();
    }

    /**
     * 连接打印机
     */
    public void connect() {
        printerAPI.connect(new USBAPI(mContext));
    }

    /**
     * 断开打印机
     */
    public void disconnect() {
        printerAPI.disconnect();
    }

    /**
     * 判断是否连接打印机
     *
     * @return
     */
    public boolean isConnected() {
        return printerAPI.isConnect();
    }

    /**
     * 设置打印机输出
     *
     * @param output
     */
    public void setOutput(boolean output) {
        printerAPI.setOutput(output);
    }

    /**
     * 获取打印机状态（研科打印机）
     * 返回的状态跟厂家提供的文档对不上，机头抬杠已打开实际测试是1065（文档里是0x20，换算成十进制是32）
     * <p>
     * 0 正常
     * 0x01 脱机
     * 0x02 按纸键接通
     * 0x04 发生错误
     * 0x08 打印纸用完，停止打印
     * 0x10 通过进纸键进纸
     * 0x20 机头抬杠已打开
     * 0x40 出现可自动恢复的错误
     * 0x80 出现不可恢复的错误
     * 0x100 发生机械错误
     * 0x200 纸将尽检测器检测到纸张接近末端
     * 0x400 纸尽传感器检测到卷纸末端
     * <p>
     * -11 发送数据失败
     * -12 读取数据失败
     */
    public int getStatus() {
        return printerAPI.getStatus();
    }

    /**
     * 打印换行
     */
    public void printFeed() {
        printerAPI.printFeed();
    }

    /**
     * 打印并退纸
     *
     * @param n - 退纸 n/144 英寸
     */
    public void printBackFlow(int n) {
        printerAPI.printBackFlow(n);
    }

    /**
     * 打印并进纸
     *
     * @param n - 进纸 – n * 0.125 毫米
     */
    public void printAndFeedPaper(int n) {
        printerAPI.printAndFeedPaper(n);
    }

    /**
     * 将打印机纸进纸到初始位置
     */
    public void feedToStartPos() {
        printerAPI.feedToStartPos();
    }

    /**
     * 切纸，全切
     */
    public void fullCut() {
        printerAPI.fullCut();
    }

    /**
     * 半切
     */
    public void halfCut() {
        printerAPI.halfCut();
    }

    /**
     * 设置对齐方式，只对文本指令有效
     *
     * @param type - 0 为左对齐 ,1 为居中对齐,2 为右对齐
     */
    public void setAlignMode(int type) {
        printerAPI.setAlignMode(type);
        printerAPI.setLeftMargin(20, 20);
    }

    /**
     * 打印机光栅图
     *
     * @param bitmap 图片
     */
    public void printRasterBitmap(Bitmap bitmap) {
        try {
            printerAPI.printRasterBitmap(bitmap, true);
//            printerAPI.printImageForPin(bitmap);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 打印照片
     *
     * @param bitmap
     */
    public void printBitmap(Bitmap bitmap) {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                //之前填576打印576*768的图片会报错，填574就不会
                //Gray8 (打印出来白一些)
                //printerAPI.printEightColorBitmap(bitmap, 574);
                //Gray16(打印出来黑一些)
                printerAPI.printSixteenColorBitmap(bitmap, 574);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void printBitmapA3(Bitmap bitmap){
        Bitmap bmp = BitmapUtils.reSizeByWidth(bitmap, 297 * 8);
        printerAPI.sendOrder(BitmapUtils.parseBmpToByte(GGR_Image.threshold(bmp)));
        printerAPI.printFeed();
        printerAPI.cutPaper(0x42,0x00);
    }

    /**
     * 切纸
     */
    public void cutPaper() {
        //printerAPI.cutPaper(0x42, 0x00);
        //为了四周空白变少，厂家让调用下面这个方法切纸,
        printerAPI.sendOrder(new byte[]{0x1d, 0x56, 0x42, 0x00});
    }

    /**
     * 打印自检页
     */
    public void printSelfPage() {
        printerAPI.selfTestPage();
    }

    /**
     * 打印字符串
     *
     * @param text
     */
    public void printString(String text) {
        try {
            printerAPI.printString(text);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void printTxtFile() {
        byte[] bytes = readAssetsText(mContext.getAssets(), "TM80E_渐变图.txt");
        printerAPI.sendOrder(bytes);
    }

    /**
     * 读取Assets的text文件
     *
     * @param fileName
     * @return
     */
    public static byte[] readAssetsText(AssetManager assetManager, String fileName) {
        try {
            InputStream inputStream = assetManager.open(fileName);
            byte[] buffer = new byte[inputStream.available()];
            List<Byte> byteList = new ArrayList<>();
            while (inputStream.read(buffer) != -1) {
                String string = new String(buffer);
                StringBuilder stringBuilder = new StringBuilder();

                stringBuilder.append(string);
                string = stringBuilder.toString();
                string = string.trim();
                string = string.replaceAll(" |,|\r|\n|#", "");
                if (string.length() % 2 != 0) {
                    string = string.substring(0, string.length() - 2);
                }
                byteList.addAll(hexStr2Str(string));
            }
            inputStream.close();
            byte[] bytes = new byte[byteList.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = byteList.get(i);
            }
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 字符串转为字节集合
     *
     * @param hexStr
     * @return
     */
    public static List<Byte> hexStr2Str(String hexStr) {
        char[] hexs = hexStr.toCharArray();
        List<Byte> bytes = new ArrayList<>();
        int value;
        for (int i = 0; i < hexStr.length() / 2; i++) {
            value = ((charToByte(hexs[2 * i]) << 4 | charToByte(hexs[2 * i + 1])));
            bytes.add((byte) (value & 0xff));
        }
        return bytes;
    }

    /**
     * 字符转换为字节
     *
     * @param c
     * @return
     */
    private static byte charToByte(char c) {
        switch (c) {
            case '0':
                return 0x00;
            case '1':
                return 0x01;
            case '2':
                return 0x02;
            case '3':
                return 0x03;
            case '4':
                return 0x04;
            case '5':
                return 0x05;
            case '6':
                return 0x06;
            case '7':
                return 0x07;
            case '8':
                return 0x08;
            case '9':
                return 0x09;
            case 'A':
            case 'a':
                return 0x0a;
            case 'B':
            case 'b':
                return 0x0b;
            case 'C':
            case 'c':
                return 0x0c;
            case 'D':
            case 'd':
                return 0x0d;
            case 'E':
            case 'e':
                return 0x0e;
            case 'F':
            case 'f':
                return 0x0f;
        }
        return 0;
    }

}
