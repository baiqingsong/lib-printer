package com.dawn.util_fun;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LLog {
    private static String TAG = "tag";
    private static boolean LOG_DEBUG = true;
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final int VERBOSE = 2;
    private static final int DEBUG = 3;
    private static final int INFO = 4;
    private static final int WARN = 5;
    private static final int ERROR = 6;
    private static final int ASSERT = 7;
    private static final int JSON = 8;
    private static final int XML = 9;
    private static final char CHAR_VERBOSE = 'v';
    private static final char CHAR_DEBUG = 'd';
    private static final char CHAR_INFO = 'i';
    private static final char CHAR_WARN = 'w';
    private static final char CHAR_ERROR = 'e';

    private static final int JSON_INDENT = 4;
    private static final int MAX_LOG_SIZE = 4000;
    private static final long MAX_LOG_FOLDER_SIZE = 500 * 1024 * 1024; // 500MB
    private static final long MAX_LOG_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static String logPath = null;

    // 修复：使用兼容的 ThreadLocal 初始化方式
    private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        }
    };

    private static final ThreadLocal<SimpleDateFormat> dateFormatLog = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        }
    };

    // RxJava components for async operations
    private static final CompositeDisposable disposables = new CompositeDisposable();
    private static final AtomicBoolean writeInProgress = new AtomicBoolean(false);

    // 队列过大时，丢弃低优先级日志，避免开机阶段日志风暴导致 CPU/I/O 飙升
    private static final int MAX_QUEUE_SIZE = 2000;

    private static final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean isWriting = new AtomicBoolean(false);

    /**
     * 初始化日志
     */
    public static void init(Context context, boolean isDebug, String tag) {
        TAG = tag;
        LOG_DEBUG = isDebug;
        logPath = getFilePath(context);
        checkFilePath(logPath);
    }

    public static void init(Context context, boolean isDebug, String tag, String logPath2) {
        TAG = tag;
        LOG_DEBUG = isDebug;
        logPath = TextUtils.isEmpty(logPath2) ? getFilePath(context) : logPath2;
        checkFilePath(logPath);
    }

    /**
     * VERBOSE日志
     */
    public static void v(String msg) {
        if (!LOG_DEBUG) return;
        log(VERBOSE, TAG, msg);
        asyncWriteToFile(CHAR_VERBOSE, TAG, msg);
    }

    public static void v(String tag, String msg) {
        if (!LOG_DEBUG) return;
        log(VERBOSE, tag, msg);
        asyncWriteToFile(CHAR_VERBOSE, tag, msg);
    }

    /**
     * DEBUG日志
     */
    public static void d(String msg) {
        if (!LOG_DEBUG) return;
        log(DEBUG, TAG, msg);
        asyncWriteToFile(CHAR_DEBUG, TAG, msg);
    }

    public static void d(String tag, String msg) {
        if (!LOG_DEBUG) return;
        log(DEBUG, tag, msg);
        asyncWriteToFile(CHAR_DEBUG, tag, msg);
    }

    /**
     * INFO日志
     */
    public static void i(Object... msg) {
        if (!LOG_DEBUG) return;
        StringBuilder sb = new StringBuilder();
        for (Object obj : msg) {
            sb.append(obj).append(",");
        }
        // 使用更准确的调用者信息进行打印和写文件
        logWithCaller(INFO, null, sb.toString());
    }

    // Helper: find the most relevant caller StackTraceElement (skip LLog, synthetic, framework)
    private static StackTraceElement findCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement candidate = null;
        for (StackTraceElement element : stackTrace) {
            if (element == null) continue;
            String className = element.getClassName();
            // skip this util class itself
            if (className.equals(LLog.class.getName())) continue;
            // skip Thread / JVM frames
            if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.")) continue;
            // skip Android/framework and kotlin/Rx frames
            if (className.startsWith("android.") || className.startsWith("kotlin.") || className.startsWith("io.reactivex.")
                    || className.startsWith("com.android.")) continue;
            // skip synthetic/lambda classes
            if (className.contains("$$") || className.contains("ExternalSynthetic") || className.contains("lambda$")) continue;

            // prefer app package classes if available
            if (className.startsWith("com.dawn")) {
                return element;
            }

            if (candidate == null) {
                candidate = element;
            }
        }
        return candidate != null ? candidate : (stackTrace.length > 0 ? stackTrace[stackTrace.length - 1] : null);
    }

    // Core logging that uses a caller-derived header (bypasses wrapperContent stack-scan)
    private static void logWithCaller(int logType, String tagStr, Object objects) {
        if (!LOG_DEBUG && logType < ERROR) return;

        if (TextUtils.isEmpty(tagStr)) tagStr = TAG;

        String msg = (objects == null || objects.toString().isEmpty()) ? "null" : objects.toString();

        StackTraceElement targetElement = findCaller();
        String headString = "";
        if (targetElement != null) {
            String className = targetElement.getClassName().replaceAll("\\$\\d+", "");
            String[] classNameInfo = className.split("\\.");
            if (classNameInfo.length > 0) {
                className = classNameInfo[classNameInfo.length - 1] + ".java";
            }
            String methodName = targetElement.getMethodName();
            int lineNumber = Math.max(targetElement.getLineNumber(), 0);
            headString = "[(" + className + ":" + lineNumber + ")#" + methodName + "] ";
        }

        switch (logType) {
            case VERBOSE:
            case DEBUG:
            case INFO:
            case WARN:
            case ERROR:
            case ASSERT:
                printDefault(logType, tagStr, headString + msg);
                break;
            case JSON:
                printJson(tagStr, msg, headString);
                break;
            case XML:
                printXml(tagStr, msg, headString);
                break;
        }

        // async write to file with header included
        asyncWriteToFile(logType == INFO ? CHAR_INFO : (logType == ERROR ? CHAR_ERROR : CHAR_DEBUG), tagStr, headString + msg);
    }

    /**
     * WARN日志
     */
    public static void w(String msg) {
        if (!LOG_DEBUG) return;
        log(WARN, TAG, msg);
        asyncWriteToFile(CHAR_WARN, TAG, msg);
    }

    public static void w(String tag, String msg) {
        if (!LOG_DEBUG) return;
        log(WARN, tag, msg);
        asyncWriteToFile(CHAR_WARN, tag, msg);
    }

    /**
     * ERROR日志
     */
    public static void e(String msg) {
        String errorStr = msg;
        // include throwable stack if any is passed separately; keep original behavior
        logWithCaller(ERROR, TAG, errorStr);
    }

    public static void e(String tag, String msg) {
        String errorStr = msg;
        logWithCaller(ERROR, tag, errorStr);
    }

    public static void e(String msg, Throwable tr) {
        String errorStr = msg + "\n" + getThrowableStr(tr);
        logWithCaller(ERROR, TAG, errorStr);
    }

    public static void e(String tag, String msg, Throwable tr) {
        String errorStr = msg + "\n" + getThrowableStr(tr);
        logWithCaller(ERROR, tag, errorStr);
    }

    /**
     * JSON日志
     */
    public static void json(String json) {
        log(JSON, TAG, json);
    }

    public static void json(String tag, String json) {
        log(JSON, tag, json);
    }

    /**
     * XML日志
     */
    public static void xml(String xml) {
        log(XML, TAG, xml);
    }

    public static void xml(String tag, String xml) {
        log(XML, tag, xml);
    }

    /**
     * 异常日志
     */
    public static void exception(Exception e) {
        asyncWriteToFile(CHAR_ERROR, TAG, Log.getStackTraceString(e));
    }

    public static void exception(String tag, Exception e) {
        asyncWriteToFile(CHAR_ERROR, tag, Log.getStackTraceString(e));
    }

    /**
     * 核心日志处理方法
     */
    private static void log(int logType, String tagStr, Object objects) {
        if (!LOG_DEBUG && logType < ERROR) return;

        String[] contents = wrapperContent(tagStr, objects);
        String tag = contents[0];
        String msg = contents[1];
        String headString = contents[2];

        switch (logType) {
            case VERBOSE:
            case DEBUG:
            case INFO:
            case WARN:
            case ERROR:
            case ASSERT:
                printDefault(logType, tag, headString + msg);
                break;
            case JSON:
                printJson(tag, msg, headString);
                break;
            case XML:
                printXml(tag, msg, headString);
                break;
        }
    }

    /**
     * 日志分块打印
     */
    private static void printDefault(int type, String tag, String msg) {
        if (TextUtils.isEmpty(tag)) tag = TAG;

        int length = msg.length();
        if (length <= MAX_LOG_SIZE) {
            printSub(type, tag, msg);
            return;
        }

        printLine(tag, true);
        for (int i = 0; i < length; i += MAX_LOG_SIZE) {
            int end = Math.min(length, i + MAX_LOG_SIZE);
            printSub(type, tag, msg.substring(i, end));
        }
        printLine(tag, false);
    }

    /**
     * 实际日志打印
     */
    private static void printSub(int type, String tag, String sub) {
        if (TextUtils.isEmpty(tag)) tag = TAG;

        printLine(tag, true);
        switch (type) {
            case VERBOSE: Log.v(tag, sub); break;
            case DEBUG: Log.d(tag, sub); break;
            case INFO: Log.i(tag, sub); break;
            case WARN: Log.w(tag, sub); break;
            case ERROR: Log.e(tag, sub); break;
            case ASSERT: Log.wtf(tag, sub); break;
        }
        printLine(tag, false);
    }

    /**
     * JSON美化打印
     */
    private static void printJson(String tag, String json, String headString) {
        if (TextUtils.isEmpty(json)) {
            d("Empty/Null json content");
            return;
        }
        if (TextUtils.isEmpty(tag)) tag = TAG;

        String message;
        try {
            if (json.startsWith("{")) {
                message = new JSONObject(json).toString(JSON_INDENT);
            } else if (json.startsWith("[")) {
                message = new JSONArray(json).toString(JSON_INDENT);
            } else {
                message = json;
            }
        } catch (JSONException e) {
            message = json;
        }

        printLine(tag, true);
        message = headString + LINE_SEPARATOR + message;
        String[] lines = message.split(LINE_SEPARATOR);
        for (String line : lines) {
            Log.d(tag, "|" + line);
        }
        printLine(tag, false);
    }

    /**
     * XML美化打印
     */
    private static void printXml(String tag, String xml, String headString) {
        if (TextUtils.isEmpty(tag)) tag = TAG;

        if (!TextUtils.isEmpty(xml)) {
            try {
                Source xmlInput = new StreamSource(new StringReader(xml));
                StringWriter stringWriter = new StringWriter();
                StreamResult xmlOutput = new StreamResult(stringWriter);

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(xmlInput, xmlOutput);

                xml = stringWriter.toString().replaceFirst(">", ">\n");
            } catch (TransformerException e) {
                Log.e(tag, "XML transform error", e);
            }
            xml = headString + "\n" + xml;
        } else {
            xml = headString + "Log with null object";
        }

        printLine(tag, true);
        String[] lines = xml.split(LINE_SEPARATOR);
        for (String line : lines) {
            if (!TextUtils.isEmpty(line)) {
                Log.d(tag, "|" + line);
            }
        }
        printLine(tag, false);
    }

    /**
     * 日志头部信息生成
     */
    private static String[] wrapperContent(String tag, Object... objects) {
        if (TextUtils.isEmpty(tag)) tag = TAG;

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 修复：更可靠的堆栈跟踪索引
        int index = 0;
        for (; index < stackTrace.length; index++) {
            if (stackTrace[index].getClassName().equals(LLog.class.getName())) {
                break;
            }
        }
        StackTraceElement targetElement = stackTrace[Math.min(index + 5, stackTrace.length - 1)];

        String className = targetElement.getClassName().replaceAll("\\$\\d+", "");
        String[] classNameInfo = className.split("\\.");
        if (classNameInfo.length > 0) {
            className = classNameInfo[classNameInfo.length - 1] + ".java";
        }

        String methodName = targetElement.getMethodName();
        int lineNumber = Math.max(targetElement.getLineNumber(), 0);
        String headString = "[(" + className + ":" + lineNumber + ")#" + methodName + "] ";

        return new String[]{
                tag,
                (objects == null || objects.length == 0) ? "null" : getObjectsString(objects),
                headString
        };
    }

    /**
     * 多参数处理
     */
    private static String getObjectsString(Object... objects) {
        if (objects.length > 1) {
            StringBuilder sb = new StringBuilder("\n");
            for (int i = 0; i < objects.length; i++) {
                Object obj = objects[i];
                sb.append("param[")
                        .append(i)
                        .append("] = ")
                        .append(obj == null ? "null" : obj.toString())
                        .append("\n");
            }
            return sb.toString();
        } else {
            return objects[0] == null ? "null" : objects[0].toString();
        }
    }

    /**
     * 日志分隔线
     */
    private static void printLine(String tag, boolean isTop) {
        if (TextUtils.isEmpty(tag)) tag = TAG;
        Log.d(tag, isTop ?
                "╔════════════════════════════════════════════════════════════════" :
                "╚════════════════════════════════════════════════════════════════");
    }

    /**
     * 获取日志存储路径
     */
    public static String getFilePath(Context context) {
        File extDir = context.getExternalFilesDir(null);
        File intDir = context.getFilesDir();

        if (intDir != null && intDir.exists()) {
            return intDir.getAbsolutePath() + "/Logs/";
        } else if (extDir != null && extDir.exists()) {
            return extDir.getAbsolutePath() + "/Logs/";
        }
        return context.getCacheDir().getAbsolutePath() + "/Logs/";
    }

    /**
     * 检查日志目录
     */
    public static void checkFilePath(String pathName) {
        File dir = new File(pathName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create log directory: " + pathName);
            }
            return;
        }

        long size = getFolderSize(dir);
        if (size > MAX_LOG_FOLDER_SIZE) {
            deleteFolder(dir);
        }
    }

    /**
     * 检查单个日志文件大小
     */
    private static void checkFileSize(String fileName) {
        File file = new File(fileName);
        if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete oversized log file");
            }
        }

        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Log.e(TAG, "Failed to create parent directories");
                    return;
                }
                if (!file.createNewFile()) {
                    Log.e(TAG, "Failed to create log file");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error creating log file", e);
            }
        }
    }

    /**
     * 计算文件夹大小
     */
    private static long getFolderSize(File file) {
        long size = 0;
        File[] fileList = file.listFiles();
        if (fileList == null) return 0;

        for (File f : fileList) {
            if (f.isDirectory()) {
                size += getFolderSize(f);
            } else {
                size += f.length();
            }
        }
        return size;
    }

    /**
     * 删除日志文件夹
     */
    public static void deleteFolder(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFolder(f);
                }
            }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
        }
    }

    private static void asyncWriteToFile(final char type, final String tagStr, final String obj) {
        if (logPath == null || TextUtils.isEmpty(obj)) return;

        // 简单限流：队列过大优先丢弃 v/d/i（保留 w/e）
        int size = logQueue.size();
        boolean lowPriority = (type == CHAR_VERBOSE || type == CHAR_DEBUG || type == CHAR_INFO);
        if (size > MAX_QUEUE_SIZE && lowPriority) {
            return;
        }

        final String[] contents = wrapperContent(tagStr, obj);
        final String ts = dateFormatLog.get().format(new Date());
        String log = ts + " " + contents[2] + " " + contents[1] + "\n";

        logQueue.offer(log);

        if (isWriting.compareAndSet(false, true)) {
            Completable.fromAction(() -> {
                        // 批量写：一次 flush 一批，避免每条日志都 open/close 文件导致 I/O 抖动
                        final String fileName = getFileName(new Date());
                        checkFilePath(logPath);
                        checkFileSize(fileName);

                        BufferedWriter bw = null;
                        try {
                            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, true)));
                            int wrote = 0;
                            while (!logQueue.isEmpty()) {
                                String logEntry = logQueue.poll();
                                if (logEntry != null) {
                                    bw.write(logEntry);
                                    wrote++;
                                    // 每写一段就 flush 一次，避免一次性写太多占用 IO 时间片
                                    if (wrote % 200 == 0) {
                                        bw.flush();
                                    }
                                }
                            }
                            bw.flush();
                        } catch (IOException e) {
                            Log.e(TAG, "Log write error: " + e.getMessage());
                        } finally {
                            if (bw != null) {
                                try { bw.close(); } catch (IOException ignore) {}
                            }
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .doFinally(() -> isWriting.set(false))
                    .onErrorComplete(throwable -> {
                        Log.e(TAG, "Async write failed: " + throwable.getMessage());
                        return true;
                    })
                    .subscribe();
        }
    }

    /**
     * 获取日志路径
     */
    public static String getLogPath() {
        return logPath;
    }

    /**
     * 生成日志文件名
     */
    public static String getFileName(Date date) {
        return logPath + "log_" + dateFormat.get().format(date) + ".txt";
    }

    /**
     * 获取异常堆栈
     */
    private static String getThrowableStr(Throwable tr) {
        if (tr == null) return "Null throwable";

        StringBuilder err = new StringBuilder();
        StackTraceElement[] stacks = tr.getStackTrace();
        for (StackTraceElement stack : stacks) {
            err.append("\tat ").append(stack.toString()).append("\n");
        }
        return err.toString();
    }

    /**
     * 清理资源
     */
    public static void dispose() {
        if (!disposables.isDisposed()) {
            disposables.dispose();
        }
    }
}
