package com.dawn.printers.uv.model;

/**
 * 打印任务模型
 */
public class PrintModel extends BasePrintModel {
    // {"event":"01","order_id":"20000196","name":"银币","file":"C:/images/11.jpg","width":"38","height":"38","left":"0","top":"0","white_ink":"0","channel_type":"1","channel":"1"}

    private String order_id;// 调用方的订单编号，由调用方自定义，支持32位以内的字符串，如 20000196
    private String name;// 商品名称，如 银币
    private String file;// 本地的图片路径或者线上的的URL地址，如 C:/images/11.jpg 或者 https://img.colorpark.cn/api/render/36531651306594235.png
    private String width;//需要打印的图片宽度 ，单位mm
    private String height;// 需要打印的图片高度 ，单位mm
    private String left;// 需要打印的图片左边距 ，单位mm
    private String top;// 需要打印的图片上边距 ，单位mm
    private String white_ink;// 是否需要白墨，0为不打白墨，1为打印白墨
    private String channel_type;// 当前机器货道的种类， 货道只放一种币值为1， 放两种币的值为2
    private String channel;// 当前订单的金币所在货道，比如金币A放在1货道，金币B放在2货道，打印金币A时值为1，打印金币B时值为2，如果两个货道只放一种币，就保持传1即可


    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getTop() {
        return top;
    }

    public void setTop(String top) {
        this.top = top;
    }

    public String getWhite_ink() {
        return white_ink;
    }

    public void setWhite_ink(String white_ink) {
        this.white_ink = white_ink;
    }

    public String getChannel_type() {
        return channel_type;
    }

    public void setChannel_type(String channel_type) {
        this.channel_type = channel_type;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "PrintModel{" +
                "order_id='" + order_id + '\'' +
                ", name='" + name + '\'' +
                ", width='" + width + '\'' +
                ", height='" + height + '\'' +
                ", left='" + left + '\'' +
                ", top='" + top + '\'' +
                ", white_ink='" + white_ink + '\'' +
                ", channel_type='" + channel_type + '\'' +
                ", channel='" + channel + '\'' +
                '}';
    }
}
