package com.dawn.libprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.printers.PrintFactory;
import com.dawn.printers.PrinterType;
import com.dawn.printers.event.ExternalPrintEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * lib-printer 演示 Activity。
 * 展示 PrintFactory 的初始化、状态查询和打印接口；
 * 打印结果通过 EventBus ExternalPrintEvent 回调。
 */
public class MainActivity extends AppCompatActivity {

    private PrintFactory printFactory;
    private TextView tvStatus;
    private Spinner spPrinterType;

    private static final PrinterType[] PRINTER_TYPES = {
            PrinterType.HITI,
            PrinterType.DNP_RX1,
            PrinterType.DNP_620,
            PrinterType.DNP_410,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        spPrinterType = findViewById(R.id.sp_printer_type);

        String[] names = new String[PRINTER_TYPES.length];
        for (int i = 0; i < PRINTER_TYPES.length; i++) {
            names[i] = PRINTER_TYPES[i].name();
        }
        spPrinterType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));

        // 启动打印服务（AIDL 绑定）
        printFactory = PrintFactory.getInstance();
        printFactory.startService(this);

        // 注册 EventBus（接收打印结果）
        EventBus.getDefault().register(this);

        Button btnInit = findViewById(R.id.btn_init);
        btnInit.setOnClickListener(v -> {
            PrinterType type = getSelectedType();
            tvStatus.setText("正在初始化 " + type.name() + "...");
            printFactory.initPrinter(type);
        });

        Button btnStatus = findViewById(R.id.btn_status);
        btnStatus.setOnClickListener(v -> {
            printFactory.getPrinterStatus(getSelectedType());
        });

        Button btnCount = findViewById(R.id.btn_count);
        btnCount.setOnClickListener(v -> {
            printFactory.getPrinterCount(getSelectedType());
        });

        Button btnPrintTest = findViewById(R.id.btn_print_test);
        btnPrintTest.setOnClickListener(v -> {
            PrinterType type = getSelectedType();
            tvStatus.setText("正在打印 6 寸测试页... (" + type.name() + ")");
            printFactory.printImageTest(type);
        });

        Button btnPrint8Inch = findViewById(R.id.btn_print_8inch);
        btnPrint8Inch.setOnClickListener(v -> {
            PrinterType type = getSelectedType();
            // 将 8 寸测试图片从 drawable 资源复制到缓存文件
            String testPath = copyDrawableToCache(R.drawable.pic1844x2434, "test_8inch.jpg");
            if (testPath == null) {
                tvStatus.setText("无法准备 8 寸测试图片");
                return;
            }
            tvStatus.setText("正在打印 8 寸测试页... (" + type.name() + ")");
            printFactory.printImage8Inch(type, testPath, 1);
        });
    }

    private PrinterType getSelectedType() {
        return PRINTER_TYPES[spPrinterType.getSelectedItemPosition()];
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    /**
     * 将 drawable 资源复制到应用缓存目录，返回文件路径。
     *
     * @param drawableId drawable 资源 ID（如 R.drawable.pic1844x2434）
     * @param fileName  目标文件名
     * @return 文件绝对路径，失败返回 null
     */
    private String copyDrawableToCache(int drawableId, String fileName) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawableId);
            if (bitmap == null) return null;
            File cacheDir = getCacheDir();
            File outFile = new File(cacheDir, fileName);
            // 已存在则复用，避免重复写入
            if (outFile.exists()) return outFile.getAbsolutePath();
            FileOutputStream fos = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            bitmap.recycle();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // =========== EventBus 回调：接收打印机服务结果 ===========

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPrinterEvent(ExternalPrintEvent event) {
        switch (event.getEvent()) {
            case GET_STATUS:
                tvStatus.setText("状态：" + (event.isStatus() ? "就绪" : "异常") + " - " + event.getMsg());
                break;
            case GET_PRINT_COUNT:
                tvStatus.setText("剩余纸张：" + event.getRemainPaper() + " 张");
                break;
            case PRINT_IMAGE:
            case PRINT_IMAGE_8_INCH:
            case PRINT_IMAGE_TEST:
            case PRINT_IMAGE_TEST_8_INCH:
                String result = event.isStatus() ? "打印成功" : "打印失败：" + event.getMsg();
                tvStatus.setText(result);
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                break;
            case RESTART_SERVICE:
                tvStatus.setText("打印服务重启中...");
                break;
        }
    }
}

