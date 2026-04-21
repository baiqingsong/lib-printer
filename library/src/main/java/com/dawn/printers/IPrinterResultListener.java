package com.dawn.printers;

public interface IPrinterResultListener {

    void onSendPrintInitStatus(boolean status, int remainPaper, String msg);
    void onSendPrintResultStatus(boolean status, String msg);



}
