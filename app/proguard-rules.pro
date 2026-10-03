# ---------------- 压缩库：7z / RAR 编解码器基于反射与枚举注册，需保留 ----------------
-keep class org.apache.commons.compress.archivers.sevenz.** { *; }
# LZMA2 编解码器在 Coders 静态初始化时被反射引用
-keep class org.tukaani.xz.LZMA2Options { *; }
-keep class org.tukaani.xz.** extends org.tukaani.xz.LZMA2Options { *; }
-keep class org.tukaani.xz.FilterOptions { *; }
-keep class org.tukaani.xz.AbstractIntegerOptions { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# ---------------- 媒体3：保留播放器扩展与数据源工厂 ----------------
-keep class androidx.media3.exoplayer.** { *; }
-keep interface androidx.media3.datasource.DataSource { *; }
-keep interface androidx.media3.datasource.DataSourceFactory { *; }
-dontwarn androidx.media3.**

# ---------------- 保留行号与源文件名，便于崩溃定位 ----------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
