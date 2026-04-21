package com.dawn.printers.dnp;

import com.saika.dnpprintersdk.api.callback.ConnectionCallback;
import com.saika.dnpprintersdk.api.callback.OrderCallback;
import com.saika.dnpprintersdk.model.PrintOrder;
import com.saika.dnpprintersdk.model.PrintTask;
import com.saika.dnpprintersdk.model.PrinterInfo;

/**
 * 新 SDK 未提供 Adapter 类时的空实现基类，便于只重写关心的回调。
 */
final class DnpSdkCallbackAdapters {

    private DnpSdkCallbackAdapters() {
    }

    static class ConnectionAdapter implements ConnectionCallback {
        @Override
        public void onPrinterDiscovered(String printerId, PrinterInfo info) {
        }

        @Override
        public void onConnected(String printerId, PrinterInfo info) {
        }

        @Override
        public void onDisconnected(String printerId, String reason) {
        }

        @Override
        public void onConnectionError(String printerId, int errorCode, String message) {
        }

        @Override
        public void onPermissionRequired(String printerId) {
        }
    }

    static class OrderAdapter implements OrderCallback {
        @Override
        public void onOrderQueued(PrintOrder order) {
        }

        @Override
        public void onOrderStarted(PrintOrder order) {
        }

        @Override
        public void onOrderProgress(PrintOrder order, int completedTasks, int totalTasks) {
        }

        @Override
        public void onOrderCompleted(PrintOrder order) {
        }

        @Override
        public void onOrderFailed(PrintOrder order, String message) {
        }

        @Override
        public void onOrderCancelled(PrintOrder order) {
        }

        @Override
        public void onTaskUpdated(PrintOrder order, PrintTask task) {
        }
    }
}
