package com.dawn.printers.uv.model;

/**
 * 心跳包模型
 */
public class HeartModel extends BasePrintModel {
    // {"event":"00","data":"ping"}
    private String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
