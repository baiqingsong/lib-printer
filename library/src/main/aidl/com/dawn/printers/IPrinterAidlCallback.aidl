// IPrintAidlCallback.aidl
package com.dawn.printers;

// Declare any non-default types here with import statements

interface IPrinterAidlCallback {
    void onDataReceived(in Bundle data);
}