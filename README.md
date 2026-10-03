<div align="center">

# 压缩速览 · ZipPeek

**不解压，直接看。** 一款专注于「压缩包内文件预览」的轻量 Android 应用。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-24-green)
![targetSdk](https://img.shields.io/badge/targetSdk-34-green)
![size](https://img.shields.io/badge/APK-2.95MB-blue)

</div>

---

## 这是做什么的

手机自带的文件管理器几乎都不能直接预览压缩包里的内容——想看一张图、听一段录音，都得先解压出来。

「压缩速览」把这个动作彻底去掉：**打开压缩包，直接浏览、直接播放**，全程不产生任何中间文件。

<p align="center">
  <em>支持 ZIP / RAR / 7Z / TAR 系列，以及独立的 .gz / .bz2 / .xz 单文件压缩</em>
</p>

## 核心特性

### 🔓 真正的流式读取

不是"先解压再打开"，而是**按需从压缩包内直接读取**：

- 优先复用系统文件选择器返回的可随机访问文件描述符，**不复制、不落地**
- 只有遇到云盘等不可寻址的提供器时，才回退到复制到缓存目录
- 读取层做了并发安全设计，图片预加载、播放 + 缩略图可同时进行

### 📦 格式支持

| 格式 | 读取 | 压缩 | 备注 |
| :--- | :---: | :---: | :--- |
| ZIP | ✅ | ✅ | 自研解析器，支持 Zip64、UTF-8 / GBK 文件名 |
| RAR | ✅ | — | 支持 RAR 1.5 ~ RAR5（junrar 8.x） |
| 7Z | ✅ | ✅ | LZMA2 编解码，压缩档位可调 |
| TAR | ✅ | ✅ | 纯打包 |
| TAR.GZ / TAR.BZ2 / TAR.XZ | ✅ | — | |
| GZ / BZ2 / XZ（单文件） | ✅ | — | |

> ZIP 的中央目录解析、局部头定位、deflate 流式解压均为纯 Kotlin 手写实现，
> 不依赖第三方库，因此能精确控制 GBK 文件名回退与随机定位行为。

### 🖼️ 针对文件类型的浏览体验

| 类型 | 能力 |
| :--- | :--- |
| **视频** | 倍速调节（0.5×~3×）、左半屏亮度 / 右半屏音量手势、左右拖动快进快退、长按 2× 加速、双击 ±10 秒；Media3 软解，兼容主流编码 |
| **图片** | 滑动切换、双指缩放、双击放大、自动按屏幕内存动态降采样（100MB 级照片也能秒开）；API 28+ 支持动图播放 |
| **音频** | 后台播放 + 通知栏/锁屏控制、同目录曲目连续播放、进度拖动、倍速、系统均衡器（多段 + 预设） |
| **文本 / 代码** | 编码自动识别（BOM → 严格 UTF-8 校验 → GB18030 回退）、字号调整、等宽切换、行号、关键词定位 |
| **其他** | 一键提取到本地后用其他应用打开 |

### 🗜 压缩与解压

- **解压**：多选 / 全部解压，实时进度与速度，可随时取消；写入用户指定目录，自动阻断 Zip-Slip 路径穿越
- **压缩**：ZIP / 7Z / TAR.GZ 三种格式，三档强度，支持多选文件或整个文件夹（递归）

### 🔒 隐私

- **不申请任何存储权限**，全部通过系统文件选择器（SAF / DocumentsContract）访问
- **没有 `INTERNET` 权限**，完全离线，不上传任何数据
- 仅申请必要的后台播放与通知权限

## 构建

### 环境要求

- JDK 17
- Android SDK（compileSdk 34、build-tools 34+）
- Gradle 由 wrapper 自动获取，无需预装

### 命令

```bash
git clone https://github.com/123456789-Liu1/zipeek.git
cd zipeek

./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 发布包（R8 混淆 + 资源压缩）
./gradlew :app:testDebugUnitTest  # 单元测试
```

产物位于 `app/build/outputs/apk/`。

> **关于签名**：`app/build.gradle.kts` 中 release 包默认复用本机的 debug 证书，
> 仅为方便直接安装体验。正式发布请配置自己的 keystore。

> **关于单元测试**：若工程路径包含非 ASCII 字符（如中文），
> Gradle 派生的测试 JVM 可能无法解析类路径而报 `ClassNotFoundException`。
> 这是构建环境问题而非代码问题，换到纯英文路径或 CI（见 `.github/workflows`）即可正常运行。

## 项目结构

```
app/src/main/java/com/zpeek/app/
├── core/                    压缩能力核心（纯 Kotlin / JVM，不依赖 UI）
│   ├── RandomAccess.kt      线程安全的随机访问源
│   ├── ArchiveSource.kt     打开压缩包（fd 直连 / 缓存回退）
│   ├── ArchiveEngine.kt     统一读取引擎接口
│   ├── ArchiveFactory.kt    目录树 + 引擎工厂
│   ├── Destinations.kt      解压目标抽象（本地目录 / SAF 目录树）
│   ├── Packer.kt            压缩与解压
│   ├── TextCharset.kt       编码识别
│   ├── zip/ZipArchive.kt    手写 ZIP 解析器
│   ├── sevenz/              7z（commons-compress）
│   ├── rar/                 RAR（junrar）
│   └── tar/                 TAR 系列
├── player/                  Media3 播放层
│   ├── ArchiveEntryDataSource.kt   从压缩包内供给媒体数据
│   ├── PlayerHolder.kt             全局播放器
│   └── PlaybackService.kt          后台播放服务
├── ui/                      Compose 界面
│   ├── home/                首页
│   ├── browse/              压缩包浏览
│   ├── view/                图片 / 视频 / 音频 / 文本浏览器
│   ├── compress/            新建压缩包
│   └── components/          通用组件
└── util/Format.kt           体积与时间格式化
```

## 已知限制

- 加密压缩包（ZIP / RAR / 7Z 带密码）暂不支持，会给出明确提示
- 扫描版（RAR4 部分变体）可能解析异常
- 云盘等不提供随机访问的内容提供器，打开时会复制一份到应用缓存
- 暂不支持 PDF、DOCX 等版式文档的原生渲染，可提取后用其他应用打开

## 第三方依赖

| 依赖 | 用途 | 许可证 |
| :--- | :--- | :--- |
| [AndroidX Media3](https://github.com/androidx/media) | 音视频解码 | Apache-2.0 |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | 7z / TAR / XZ 编解码 | Apache-2.0 |
| [junrar](https://github.com/junrar/junrar) | RAR 解压 | UnRAR License |
| [XZ for Java](https://tukaani.org/xz/java.html) | LZMA2 编解码 | 0BSD |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | UI 框架 | Apache-2.0 |

## License

[MIT](LICENSE) © 2026 PRCS
