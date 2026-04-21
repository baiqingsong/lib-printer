# LibPrinter

Android 多打印机驱动库，支持 **HiTi（呈研）、DNP（RX1/620/410）、ICOD（热敏）、UV 平板** 四大品牌打印机，通过独立后台 Service 进程 + AIDL 实现跨进程隔离打印，避免打印崩溃波及主进程。

## 功能特性

- **多品牌支持**：HiTi 呈研、DNP RX1/DS620/QW410、ICOD 热敏、UV 平板打印机
- **进程隔离**：`PrintService` 运行在独立 `:printer` 进程，AIDL 双向通信，打印崩溃不影响主进程
- **EventBus 回调**：打印初始化、状态查询、打印结果均通过 `ExternalPrintEvent` EventBus 回调
- **自动重连**：打印超时自动重启服务并重新发送打印指令
- **UV TCP 通信**：UV 打印机通过 TCP 长连接与硬件通信，自动重连
- **ZTL OTG 支持**：打印失败时自动切换 USB OTG 模式重试

## 引入

### Gradle

```groovy
// 根 build.gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
        flatDir { dirs 'libs' }   // 如需使用可选打印机 SDK
    }
}

// 模块 build.gradle
dependencies {
    implementation 'com.github.baiqingsong:lib-printer:1.0.0'

    // === 按需添加对应打印机 SDK（从 lib-printer 的 libs 目录获取或向厂商索取）===
    // DNP 打印机
    implementation(name: 'DNPPrinterSDK-release', ext: 'aar')
    // ICOD 热敏打印机
    implementation(name: 'icod_3.1.8_f', ext: 'jar')
    // 兄弟打印机
    implementation(name: 'lib-android-brsdk-core', ext: 'aar')
    implementation(name: 'lib-android-brsdk-print', ext: 'aar')
}
```

### AndroidManifest.xml

在消费方的 AndroidManifest 中声明打印服务：

```xml
<uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />

<application>
    <!-- 打印服务，独立 :printer 进程 -->
    <service
        android:name="com.dawn.printers.PrintService"
        android:process=":printer" />
</application>
```

## 核心类说明

| 类名 | 说明 |
| --- | --- |
| `PrintFactory` | 打印机门面（单例），管理服务绑定和打印指令发送 |
| `PrintService` | 后台打印服务（独立进程），分发打印指令到各驱动 |
| `PrinterType` | 打印机类型枚举（HITI/DNP_RX1/DNP_620/DNP_410/THERMAL/UV）|
| `PrinterManage` | 各品牌打印机 Manager 的抽象基类 |
| `HITIManager` | 呈研打印机驱动 |
| `DNPManager` | DNP 打印机驱动（Saika SDK）|
| `ICODManager` | ICOD 热敏打印机驱动 |
| `UVManager` | UV 平板打印机驱动（TCP 通信）|
| `ExternalPrintEvent` | EventBus 打印结果事件（主进程接收）|
| `PrintEvent` | AIDL 打印指令（主进程→打印服务）|
| `IPrinterCallbackListener` | 打印服务内部回调接口 |

## 基本用法

### 1. 启动打印服务

```java
// Application 或 Activity onCreate 中
PrintFactory.getInstance().startService(context);
```

### 2. 注册 EventBus（接收打印结果）

```java
// Activity.onCreate
EventBus.getDefault().register(this);

@Subscribe(threadMode = ThreadMode.MAIN)
public void onPrinterEvent(ExternalPrintEvent event) {
    switch (event.getEvent()) {
        case GET_STATUS:
            boolean ready = event.isStatus();
            break;
        case GET_PRINT_COUNT:
            int remain = event.getRemainPaper();
            break;
        case PRINT_IMAGE:
            boolean ok = event.isStatus();
            String msg = event.getMsg();
            break;
    }
}

// Activity.onDestroy
EventBus.getDefault().unregister(this);
```

### 3. 初始化打印机

```java
PrintFactory.getInstance().initPrinter(PrinterType.HITI);
// 结果通过 ExternalPrintEvent(GET_STATUS) 回调
```

### 4. 查询状态/纸张

```java
PrintFactory.getInstance().getPrinterStatus(PrinterType.HITI);
PrintFactory.getInstance().getPrinterCount(PrinterType.HITI);
```

### 5. 打印图片

```java
// 普通打印（路径、份数、是否裁剪）
PrintFactory.getInstance().printImage(PrinterType.HITI, "/sdcard/photo.jpg", 1, false);

// UV 打印（通道、尺寸、位置）
PrintFactory.getInstance().printImage(PrinterType.UV, "/sdcard/photo.jpg",
    1 /*channel*/, 100f /*width*/, 150f /*height*/, 0f /*left*/, 0f /*top*/);
```

## 支持打印机类型

| 枚举值 | 品牌/型号 | SDK 依赖 |
| --- | --- | --- |
| `HITI` | 呈研（HiTi）P520L | `printsdk2.2.1.0-api26.jar`（内置）|
| `DNP_RX1` | DNP RX1 | `DNPPrinterSDK-release.aar`（可选）|
| `DNP_620` | DNP DS620 | `DNPPrinterSDK-release.aar`（可选）|
| `DNP_410` | DNP QW410 | `DNPPrinterSDK-release.aar`（可选）|
| `THERMAL` | ICOD 热敏打印机 | `icod_3.1.8_f.jar`（可选）|
| `UV` | UV 平板打印机 | TCP 通信（内置）|

## 可选 SDK 说明

本库采用 `compileOnly` 依赖策略。核心逻辑内置，各品牌 SDK 由消费方按需自行引入。未引入某品牌 SDK 时，该品牌打印功能不可用但不影响其他品牌。

需要的 SDK 文件在 `library/libs/` 目录中，可直接复制到消费方项目的 `app/libs/` 目录使用。
