package com.dawn.printers.uv.model;

/**
 * 打印机状态模型
 */
public class PrinterStatusResultModel extends BasePrintModel{
    //{"event":"04","code":"0","status":"2","msg":"打印中","notice_time":"2025-07-19 17:08:30"}
    private String code;// 返回结果状态码，成功时为0，失败时为1
    private String status;// 打印机状态，0: 离线 1：正常 2：打印中 3：故障 4：维护  5：警告提示
    private String msg;// 状态描述信息
    private String notice_time;// 状态通知时间，格式为yyyy-MM-dd HH:mm:ss

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getNotice_time() {
        return notice_time;
    }

    public void setNotice_time(String notice_time) {
        this.notice_time = notice_time;
    }
}
