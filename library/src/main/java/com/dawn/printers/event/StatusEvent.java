package com.dawn.printers.event;

public enum StatusEvent {
    READY,// 就绪
    PRINTING,// 打印中
    ERROR,// 报错
    SUCCESS,// 打印成功
    DISCONNECTED,// 断开连接
}
