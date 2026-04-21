package com.dawn.printers.event;

import com.dawn.printers.PrinterType;

import java.io.Serializable;

public class PrintEvent implements Serializable {
    public enum EventType {
        INIT_PRINTER,// 初始化打印机
        GET_PRINTER_STATE,// 查询打印机状态
        GET_PRINT_COUNT,// 查询剩余打印张数
        PRINT_IMAGE,// 打印图片
        PRINT_IMAGE_TEST,// 测试打印图片
        PARAMETER_SETTING,// 参数设置
    }

    private EventType eventType;
    private PrinterType printerType;//当前打印机类型
    private String imagePath;//图片路径
    private int resId;//图片资源ID
    private int printNum;//打印份数
    private boolean isCut;//打印后是否切纸
    private int offsetX;//打印偏移X
    private int color;;//打印颜色
    private int channel;//打印通道
    private float width;// 打印宽度
    private float height;// 打印高度
    private float left;// 打印左边距
    private float top;// 打印上边距
    private int action;//动作

    public PrintEvent(EventType eventType, PrinterType printerType) {
        this.eventType = eventType;
        this.printerType = printerType;
    }

    public PrintEvent(EventType eventType, PrinterType printerType, String imagePath, int printNum, boolean isCut) {
        this.eventType = eventType;
        this.printerType = printerType;
        this.imagePath = imagePath;
        this.printNum = printNum;
        this.isCut = isCut;
    }

    public PrintEvent(EventType eventType, PrinterType printerType, int offsetX, int color) {
        this.eventType = eventType;
        this.printerType = printerType;
        this.offsetX = offsetX;
        this.color = color;
    }

    public PrintEvent(EventType eventType, PrinterType printerType, int action) {
        this.eventType = eventType;
        this.printerType = printerType;
        this.action = action;
    }

    public PrintEvent(EventType eventType, PrinterType printerType, String imagePath, int channel, float width, float height, float left, float top) {
        this.eventType = eventType;
        this.printerType = printerType;
        this.imagePath = imagePath;
        this.channel = channel;
        this.width = width;
        this.height = height;
        this.left = left;
        this.top = top;
    }

    public PrintEvent(EventType eventType, PrinterType printerType, int resId, int channel, float width, float height, float left, float top) {
        this.eventType = eventType;
        this.printerType = printerType;
        this.resId = resId;
        this.channel = channel;
        this.width = width;
        this.height = height;
        this.left = left;
        this.top = top;
    }

    public EventType getEvent() {
        return eventType;
    }

    public PrinterType getPrinterType() {
        return printerType;
    }

    public String getImagePath() {
        return imagePath;
    }

    public int getPrintNum() {
        return printNum;
    }

    public boolean isCut() {
        return isCut;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getColor() {
        return color;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getAction() {
        return action;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public int getResId() {
        return resId;
    }
}
