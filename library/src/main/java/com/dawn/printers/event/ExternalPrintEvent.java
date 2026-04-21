package com.dawn.printers.event;

import java.io.Serializable;

public class ExternalPrintEvent implements Serializable {
    public enum EventType {
        GET_STATUS,// 获取状态
        GET_PRINT_COUNT,// 获取剩余纸张数
        PRINT_IMAGE_TEST,// 打印测试页
        PRINT_IMAGE,// 打印图片
        RESTART_SERVICE// 重启打印服务
    }

    private EventType eventType;
    private String msg;//成功或失败信息
    private boolean status;// 状态
    private int remainPaper;// 剩余纸张数
    /*查询状态返回结果, 打印返回结果*/
    public ExternalPrintEvent(EventType eventType, boolean status, String msg) {
        this.eventType = eventType;
        this.status = status;
        this.msg = msg;
    }
    /*查询数量返回结果*/
    public ExternalPrintEvent(EventType eventType, int remainPaper, String msg) {
        this.eventType = eventType;
        this.remainPaper = remainPaper;
        this.msg = msg;
    }

    public ExternalPrintEvent(EventType eventType) {
        this.eventType = eventType;
    }

    public EventType getEvent() {
        return eventType;
    }

    public String getMsg() {
        return msg;
    }

    public boolean isStatus() {
        return status;
    }

    public int getRemainPaper() {
        return remainPaper;
    }
}
