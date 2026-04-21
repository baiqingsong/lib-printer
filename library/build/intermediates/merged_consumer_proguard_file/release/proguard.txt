# ProGuard rules for lib-printer consumers
-keep class com.dawn.printers.** { *; }
-keep interface com.dawn.printers.** { *; }
-keep enum com.dawn.printers.** { *; }
-keep class com.dawn.util_fun.** { *; }
-keep class com.dawn.tcp.** { *; }

# DNP SDK
-dontwarn com.saika.dnpprintersdk.**
-keep class com.saika.dnpprintersdk.** { *; }

# HiTi SDK
-dontwarn com.hiti.**
-keep class com.hiti.** { *; }

# ICOD SDK
-dontwarn icod.**
-keep class icod.** { *; }

# ZTL API
-dontwarn ZtlApi.**
-keep class ZtlApi.** { *; }

# EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
