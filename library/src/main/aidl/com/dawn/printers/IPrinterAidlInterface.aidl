// IPrinterAidlInterface.aidl
package com.dawn.printers;

// Declare any non-default types here with import statements
import com.dawn.printers.IPrinterAidlCallback;
interface IPrinterAidlInterface {
        int getProcessId();
        void sendData(in Bundle data);
        void registerCallback(IPrinterAidlCallback callback);
        void unregisterCallback(IPrinterAidlCallback callback);
}