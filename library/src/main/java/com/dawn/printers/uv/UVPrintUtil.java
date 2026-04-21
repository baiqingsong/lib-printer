package com.dawn.printers.uv;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.dawn.printers.R;
import com.dawn.printers.uv.model.BasePrintModel;
import com.dawn.printers.uv.model.HeartModel;
import com.dawn.printers.uv.model.PrintModel;
import com.dawn.printers.uv.model.PrintStatusResultModel;
import com.dawn.printers.uv.model.PrinterStatusResultModel;
import com.dawn.tcp.TcpConfig;
import com.dawn.tcp.TcpFactory;
import com.dawn.util_fun.LJsonUtil;
import com.dawn.util_fun.LLog;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UVPrintUtil {
    // 单例模式
    private static UVPrintUtil instance;

    public UVPrintUtil() {}

    private TcpFactory.Handle tcpClientHandle;

    /** Ensure all socket writes happen off the main thread and keep message order. */
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("uv-print-send");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动 TCP 客户端（自动重连）
     */
    public synchronized void startTcpClient(String host, int port) {
        if (tcpClientHandle != null) return;
        if (TextUtils.isEmpty(host)) return;

        TcpConfig cfg = buildDefaultConfig();
        cfg.reconnectBaseDelayMs = 500;
        cfg.reconnectMaxDelayMs = 8000;

        try {
            tcpClientHandle = TcpFactory.startJsonClient(host, port, cfg, new TcpFactory.JsonCallbacks() {
                @Override
                public void onConnecting(InetSocketAddress remote) {
                    LLog.i("[TCP][Client] connecting: " + remote);

                }

                @Override
                public void onConnected(InetSocketAddress remote) {
                    LLog.i("[TCP][Client] connected: " + remote);

                }

                @Override
                public void onDisconnected(InetSocketAddress remote, Exception cause) {
                    // TcpClient 内部会自动connectLoop，断开后会进入重连
                    LLog.e("[TCP][Client] disconnected, will reconnect... remote=" + remote + ", cause=" + (cause == null ? "null" : cause.getMessage()));
                    if(printerStatusListener != null){
                        PrinterStatusResultModel printStatusResultModel = new PrinterStatusResultModel();
                        printStatusResultModel.setStatus("-1");
                        printStatusResultModel.setMsg("连接断开");
                        printerStatusListener.onStatusResult(printStatusResultModel);
                    }
                }

                @Override
                public void onJsonMessage(InetSocketAddress remote, String jsonText) {
                    handleIncomingJson(false, remote, jsonText);
                }

                @Override
                public void onError(InetSocketAddress remote, Exception error) {
                    LLog.e("[TCP][Client] error: " + (error == null ? "null" : error.getMessage()));

                }
            });

            LLog.i("[TCP][Client] started, host=" + host + ", port=" + port + ", hbIntervalMs=" + cfg.heartbeatIntervalMs + ", reconnect=" + cfg.reconnectBaseDelayMs + "~" + cfg.reconnectMaxDelayMs);
        } catch (Exception e) {
            LLog.e("[TCP][Client] start failed: " + e.getMessage());
            tcpClientHandle = null;
        }
    }

    public interface SendCallback {
        void onResult(boolean ok, String errorMsg);
    }

    /**
     * Async send JSON (recommended).
     * This prevents NetworkOnMainThreadException and keeps send order.
     */
    public void sendJsonAsync(String jsonText, SendCallback callback) {
        final String payload = (jsonText == null ? "" : jsonText);
        sendExecutor.execute(() -> {
            boolean ok = false;
            String err = null;
            try {
                if (tcpClientHandle == null) {
                    err = "handle is null";
                    return;
                }
                if (!tcpClientHandle.isConnected()) {
                    err = "not connected";
                    return;
                }
                TcpFactory.sendJson(tcpClientHandle, payload);
                ok = true;
            } catch (Exception e) {
                err = e.getMessage();
                if (err == null && e.getCause() != null) err = e.getCause().getMessage();
                LLog.e("[TCP][Client] sendJsonAsync error: " + (err == null ? "null" : err)
                        + ", ex=" + e.getClass().getSimpleName()
                        + ", connected=" + (tcpClientHandle != null && tcpClientHandle.isConnected())
                        + ", jsonLen=" + payload.length());
                LLog.e("[TCP][Client] sendJsonAsync stack", e);
            } finally {
                if (callback != null) {
                    try { callback.onResult(ok, err); } catch (Exception ignore) {}
                }
            }
        });
    }

    /**
     * Kept for compatibility with existing callers.
     * This enqueues the send work to the background single-thread executor.
     */
    public boolean sendJsonInternal(String jsonText) {
        if (tcpClientHandle == null) {
            LLog.e("[TCP][Client] sendJson ignored: handle is null");
            return false;
        }
        if (!tcpClientHandle.isConnected()) {
            LLog.e("[TCP][Client] sendJson ignored: not connected");
            return false;
        }
        sendJsonAsync(jsonText, null);
        return true;
    }

    /** 停止 TCP（服务端/客户端）。 */
    public synchronized void stopTcp() {
        try {
            if (tcpClientHandle != null) {
                tcpClientHandle.stop();
                tcpClientHandle = null;
            }
        } catch (Exception e) {
            LLog.e("[TCP] stopTcp error: " + e.getMessage());
        }
        try {
            sendExecutor.shutdownNow();
        } catch (Exception ignore) {
        }
    }

    private TcpConfig buildDefaultConfig() {
        TcpConfig cfg = new TcpConfig();
        // 对端是纯 JSON 文本流：不带 [int32 length] 头，所以这里切换为 RAW_JSON_STREAM。
        cfg.protocolMode = TcpConfig.ProtocolMode.RAW_JSON_STREAM;

        HeartModel heart = new HeartModel();
        heart.setEvent("00");
        heart.setData("ping");
        cfg.heartbeatText = LJsonUtil.objToJson(heart);
        cfg.heartbeatIntervalMs = 10_000;
        cfg.readTimeoutMs = 15_000;
        cfg.idleTimeoutMs = 30_000;
        cfg.keepAlive = true;
        cfg.tcpNoDelay = true;
        return cfg;
    }
    /**
     * 收到 JSON 文本：判断是业务 TCPModel 还是文件分片。
     */
    private void handleIncomingJson(boolean isServer, InetSocketAddress remote, String jsonText) {
        if (TextUtils.isEmpty(jsonText)) return;

        // TcpFactory 已过滤掉 heartbeatText，所以这里不会收到 PING。

        LLog.i("[TCP]" + (isServer ? "[Server]" : "[Client]") + " received JSON: " + jsonText);
        try{
            BasePrintModel baseModel = LJsonUtil.jsonToObj(jsonText, BasePrintModel.class);
            if(baseModel != null && !TextUtils.isEmpty(baseModel.getEvent())){
                switch (baseModel.getEvent()){
                    case "00"://心跳不处理
                        break;
                    case "01"://打印返回状态
                        LLog.e("开始打印状态返回处理");
                        break;
                    case "03"://打印状态返回
                        PrintStatusResultModel printStatusResultModel = LJsonUtil.jsonToObj(jsonText, PrintStatusResultModel.class);
                        if(printerStatusListener != null){
                            printerStatusListener.onPrintResult(printStatusResultModel);
                        }
                        break;
                    case "04"://打印机状态返回
                        PrinterStatusResultModel statusResult = LJsonUtil.jsonToObj(jsonText, PrinterStatusResultModel.class);
                        if(printerStatusListener != null){
                            printerStatusListener.onStatusResult(statusResult);
                        }
                        break;
                    case "05":// 缺墨
                        LLog.e("打印机缺墨");
                        break;
                    case "08"://装墨和停止装墨
                        LLog.e("打印机装墨和停止装墨");
                        break;
                    case "09"://清洗
                        LLog.e("打印机清洗");
                        break;
                    case "10"://喷头检测
                        LLog.e("打印机喷头检测");
                        break;
                }
            }else{
                LLog.w("[TCP]" + (isServer ? "[Server]" : "[Client]") + " unknown JSON model received.");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 打印图片
     * @param context 上下文
     * @param imagePath 图片路径
     * @param channel 货道
     */
    public void sendPrintImage(Context context, String imagePath, int channel, float width, float height, float left, float top){
        try{
            PrintModel printModel = new PrintModel();
            printModel.setEvent("01");
            printModel.setOrder_id(System.currentTimeMillis() + "");
            printModel.setName("照片机");
            printModel.setWhite_ink("0");
            printModel.setChannel_type("3");
            if(channel < 1 || channel > 3){
                channel = 1;
            }
            printModel.setChannel(channel + "");
            printModel.setWidth("" + width);
            printModel.setHeight("" + height);
            printModel.setLeft("" + left);
            printModel.setTop("" + top);
            printModel.setFile(pngToBase64(context, imagePath));
            sendJsonInternal(LJsonUtil.objToJson(printModel));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String pngToBase64(Context context, String imagePath){
        try {
            if (context == null) return "";
            if (TextUtils.isEmpty(imagePath)) return "";

            java.io.File file = new java.io.File(imagePath);
            if (!file.exists() || !file.isFile()) {
                LLog.e("pngToBase64 file not found: " + imagePath);
                return "";
            }

            // Guard: avoid huge payloads (Base64 will expand ~33%).
            long len = file.length();
            if (len <= 0) return "";
            // 3MB raw file ~ 4MB base64 (approx). Adjust if your peer allows larger frames.
            long maxBytes = 3L * 1024 * 1024;
            if (len > maxBytes) {
                LLog.e("pngToBase64 file too large: " + len + " bytes, path=" + imagePath);
                // If you need to support big images, switch to chunk upload protocol instead of one JSON.
                return "";
            }

            java.io.InputStream is = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE, len));
                byte[] buf = new byte[8 * 1024];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    if (n == 0) continue;
                    baos.write(buf, 0, n);
                }
                byte[] bytes = baos.toByteArray();
                if (bytes.length == 0) return "";

                // Pure base64 (no data:image/png;base64, prefix)
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            } finally {
                try { is.close(); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            LLog.e("pngToBase64(path) error: " + e.getMessage());
            LLog.e("pngToBase64(path) stack", e);
            return "";
        }
    }

    /**
     * 测试打印
     * @param channel 通道
     */
    public void sendPrintTest(Context context, int resId, int channel, float width, float height, float left, float top){
        try{

            PrintModel printModel = new PrintModel();
            printModel.setEvent("01");
            printModel.setOrder_id(System.currentTimeMillis() + "");
            printModel.setName("照片机");
            printModel.setWhite_ink("0");
            printModel.setChannel_type("3");
            if(channel < 1 || channel > 3){
                channel = 1;
            }
            printModel.setChannel(channel + "");
            printModel.setWidth("" + width);
            printModel.setHeight("" + height);
            printModel.setLeft("" + left);
            printModel.setTop("" + top);
            printModel.setFile(pngToBase64(context, resId));
            sendJsonInternal(LJsonUtil.objToJson(printModel));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * PNG 图片资源转 Base64 字符串
     * @param context
     * @param resId
     * @return
     */
    private String pngToBase64(Context context, int resId){
        try {
            // Use application context to avoid leaking an Activity.
            if (context == null) return "";

            android.content.res.Resources res = context.getResources();
            java.io.InputStream is = res.openRawResource(resId);
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8 * 1024];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    if (n == 0) continue;
                    baos.write(buf, 0, n);
                }
                byte[] bytes = baos.toByteArray();
                if (bytes.length == 0) return "";

                // Most servers expect pure base64 without line breaks.
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            } finally {
                try { is.close(); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            LLog.e("pngToBase64 error: " + e.getMessage());
            return "";
        }
    }

    /**
     * 清洗打印头
     */
    public void sendCleanPrintHead(){
        try{
            BasePrintModel basePrintModel = new BasePrintModel();
            basePrintModel.setEvent("09");
            sendJsonInternal(LJsonUtil.objToJson(basePrintModel));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 喷头检测
     */
    public void sendCheckPrintHead(){
        try{
            BasePrintModel basePrintModel = new BasePrintModel();
            basePrintModel.setEvent("10");
            sendJsonInternal(LJsonUtil.objToJson(basePrintModel));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private OnPrinterStatusListener printerStatusListener;
    public void setOnPrinterStatusListener(OnPrinterStatusListener listener){
        this.printerStatusListener = listener;
    }

    public interface OnPrinterStatusListener {
        void onStatusResult(PrinterStatusResultModel statusResult);
        void onPrintResult(PrintStatusResultModel statusResultModel);
    }
}
