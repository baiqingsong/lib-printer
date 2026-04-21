package com.dawn.printers;

import java.util.HashMap;
import java.util.Map;

public enum PrinterType {
    NONE(-1),// 无
    HITI(0),// 呈研
    DNP_RX1(4),// DNP RX1
    DNP_620(5),// DNP 620
    DNP_410(6),// DNP 410
    LASER(7),// 激光打印机
    INKJET(8),// 喷墨打印机
    THERMAL(9),// 热敏打印机
    COLOR_LASER(10),// 彩色激光打印机
    CITIZEN(11),// 西铁城打印机
    UV(13);// UV打印机

    private final int typeValue;
    PrinterType(int value) {
        this.typeValue = value;
    }

    public int getTypeValue() {
        return typeValue;
    }
    private static final Map<Integer, PrinterType> VALUE_MAP = new HashMap<>();

    static {
        for (PrinterType t : values()) {
            VALUE_MAP.put(t.typeValue, t);
        }
    }

    /**
     * 根据 int 值获取枚举，找不到时返回 PrinterType.NONE
     */
    public static PrinterType fromInt(int value) {
        PrinterType t = VALUE_MAP.get(value);
        return t != null ? t : NONE;
    }
}
