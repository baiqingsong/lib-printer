package com.dawn.printers.uv.model;

/**
 * 打印任务回调
 */
public class PrintStatusResultModel extends BasePrintModel {
    // {"event":"03","code":"0","task_id":"10000002","status":"3","msg":"完成打印"}
    private String code;// 返回结果状态码，成功时为0，失败时为1
    private String task_id;// 分配给打印任务的唯一编号
    private String status;// 打印任务状态，1：正在打印 2：完成打印 3：取消打印 4：打印故障 5：进料失败 6：出货失败 7：归位失败 8：打印机未复位 11：库存不足（订单无法打印，后台需退款） 12： 库存预警（库存为0，下一个订单库存不足将无法打印）
    private String msg;// 状态描述信息

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTask_id() {
        return task_id;
    }

    public void setTask_id(String task_id) {
        this.task_id = task_id;
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
}
