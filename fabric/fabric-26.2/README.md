# ChatImage Fabric 26.2 移植版

基于 kitUIN/ChatImage 1.4.7，保留 MIT 授权和上游 ChatImageCode 0.12.2。
此目录是独立的 26.2 源码目标；不经过旧版 ModMultiVersionTool 生成。

## 下载与安装

这是 [zedoCN/ChatImage](https://github.com/zedoCN/ChatImage) 的非官方移植版本，上游项目为 [kitUIN/ChatImage](https://github.com/kitUIN/ChatImage)。

1. 在 [Release 下载页](https://github.com/zedoCN/ChatImage/releases/tag/v1.4.7-port.2%2B26.2) 下载 **`ChatImage-1.4.7-port.2+26.2.jar`**。`-sources.jar` 是开发源码，不能作为游戏模组安装。
2. 使用 Minecraft **26.2**、Fabric Loader **0.19.3+**、Fabric API **0.158.0+26.2** 和 **Java 25**。
3. 关闭游戏，将 JAR 放入该实例的 `mods` 目录；HMCL 可在版本管理中打开实例文件夹。已有其他版本 ChatImage 时先移出旧 JAR，避免重复加载。
4. 启动游戏。发送图片链接或 CICode，把鼠标移到绿色图片名称上查看图片；按 **End** 打开设置。

此发布仅面向 Fabric 26.2；不适用于 Forge、NeoForge 或其他 Minecraft 版本。移植版问题请提交到 [本 fork 的 Issues](https://github.com/zedoCN/ChatImage/issues)。

## 环境和构建

- Minecraft **26.2**
- Fabric Loader **0.19.3 或更高**
- Fabric API **0.158.0+26.2 或更高的 26.2 版本**
- Java **25**
- Mod Menu 可选，使用 20.0.1 验证

```sh
cd fabric/fabric-26.2
# 设置 JAVA_HOME 为本机 JDK 25 的目录后执行：
./gradlew build
```

Windows 使用 `gradlew.bat build`。

产物：`build/libs/ChatImage-1.4.7-port.2+26.2.jar`。复制到实例的 `mods` 后重启游戏。
ChatImageCode 已内嵌；本目标自行注册 `show_chatimage`，不再依赖旧版 ActionLib。

## 用法

- 发送 `[[CICode,url=https://example.com/image.png,name=图片]]`，将鼠标放到绿色图片名称上查看。
- 直接发送 PNG/JPG/GIF 等图片链接也会识别，支持显式端口。
- `/chatimage send 名称 URL`、`/chatimage url URL`、`/chatimage reload`。
- End 打开设置，或在 Mod Menu 中打开 ChatImage 配置。
- 本地文件使用 `file:///绝对路径`；拖入聊天窗口也可生成 CICode。
- 本地图片跨玩家传输需要服务器也加载对应模组。公开网络图片不依赖服务端模组。
- macOS 图片粘贴通过 AppKit 辅助进程读取 PNG/TIFF；不启动 AWT GUI，不会再以空字符串覆盖普通文本。

## 移植内容

- 使用非混淆 Mojang 名称和 Loom 1.17.11，目标 Java 25。
- 适配 26.2 `GuiGraphicsExtractor`、`Gui.hud`、聊天组件、输入事件、布局和 Fabric 网络注册 API。
- 动态纹理统一使用游戏的 `DynamicTexture`，在客户端线程创建、上传和注册。
- 在 HoverEvent codec 初始化前注册 `show_chatimage`；保留原有 JSON 协议。
- 新版聊天点击命中要求有点击事件：为 NSFW 图片加入专用点击事件，取消/确认均保留聊天输入。
- 保留非组件翻译参数，修正带端口图片链接被截断的问题。

## 实机测试

见 [MCP 验证记录](tests/MCP-VALIDATION.md)。使用用户 HMCL 实例的 **33 个启用 JAR**，逐个 SHA-256 校验副本；关闭的 `.jar.disabled` 不启用。
最终还用 HMCL 自带的游戏 JAR、依赖库和 Fabric Loader 启动正式打包产物，未使用开发源码替代 JAR。

可重跑打包测试（macOS arm64，离线测试身份，独立目录）：

```sh
python3 tests/launch_hmcl_packaged.py \
  --instance '/Applications/HMCL/.minecraft/versions/26.2-Fabric 0.19.3' \
  --game-dir run \
  --jar build/libs/ChatImage-1.4.7-port.2+26.2.jar \
  --java-home /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

测试目录、桥接配置及其认证信息、缓存、世界和运行日志被 Git 忽略。启动脚本只使用离线测试身份，不读取 HMCL 账户凭据。

## 1.4.7-port.2 更新

修复 26.2 按键设置的 ChatImage 分类标题显示原始翻译键的问题，补齐简体中文、繁体中文、英文和韩文分类翻译。End 默认键位不变。
