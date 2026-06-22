package com.dawn.printers.dnp;

/**
 * 与 {@link com.dawn.printers.PrinterType} 中 DNP 机型对应的打印通道类型。
 * 使用类常量替代 enum，避免 AGP 7.4.2 内置 D8 的 enum 处理 NPE bug。
 */
public final class DnpPrinterType {
    public static final DnpPrinterType RX1 = new DnpPrinterType("RX1");
    public static final DnpPrinterType DS620 = new DnpPrinterType("DS620");
    public static final DnpPrinterType QW410 = new DnpPrinterType("QW410");

    private final String name;

    private DnpPrinterType(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
