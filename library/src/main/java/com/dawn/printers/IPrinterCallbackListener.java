package com.dawn.printers;

public interface IPrinterCallbackListener {

    void initStatus(PrinterType printerType, boolean status, String msg);

    void getPrinterCount(PrinterType printerType, int remainPaper, String msg);

    void getPrintResult(PrinterType printerType, boolean status, String msg);
}
