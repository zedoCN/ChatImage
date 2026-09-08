# ChatImage Fabric 26.2 移植版

基于 kitUIN/ChatImage 1.4.7，保留 MIT 授权和上游 ChatImageCode 0.12.2。
此目录是独立的 26.2 源码目标；不经过旧版 ModMultiVersionTool 生成。

## 下载与安装

这是 [zedoCN/ChatImage](https://github.com/zedoCN/ChatImage) 的非官方移植版本，上游项目为 [kitUIN/ChatImage](https://github.com/kitUIN/ChatImage)。

1. 在 [Release 下载页](https://github.com/zedoCN/ChatImage/releases/tag/v1.4.7-port.6%2B26.2) 下载 **`ChatImage-1.4.7-port.6+26.2.jar`**。`-sources.jar` 是开发源码，不能作为游戏模组安装。
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

产物：`build/libs/ChatImage-1.4.7-port.6+26.2.jar`。复制到实例的 `mods` 后重启游戏。
ChatImageCode 已内嵌；本目标自行注册 `show_chatimage`，不再依赖旧版 ActionLib。

## 用法

- 发送 `[[CICode,url=https://example.com/image.png,name=图片]]`，将鼠标放到绿色图片名称上查看。
- 直接发送 PNG/JPG/GIF 等图片链接也会识别，支持显式端口。
- `/chatimage send 名称 URL`、`/chatimage url URL`、`/chatimage reload`。
- End 打开设置，或在 Mod Menu 中打开 ChatImage 配置。
- 本地文件使用 `file:///绝对路径`；拖入聊天窗口也可生成 CICode。
- 本地图片跨玩家传输需要服务器也加载对应模组。公开网络图片不依赖服务端模组。服务端托管要求双方使用 port.3+，不再使用旧版基于本机路径的传输协议。
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
  --jar build/libs/ChatImage-1.4.7-port.6+26.2.jar \
  --java-home /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

测试目录、桥接配置及其认证信息、缓存、世界和运行日志被 Git 忽略。启动脚本只使用离线测试身份，不读取 HMCL 账户凭据。


## port.3：可选服务端图片托管

客户端与 Fabric 服务器可安装同一个 JAR。只装客户端时仍可查看网络图片；服务器也装 port.3+ 后，才启用本地图片上传。

- **拖入 / 粘贴**：打开聊天框，将文件拖入或粘贴剪贴板图片；检查输入内容后按 Enter，上传成功才发送图片引用。每条消息支持一张本地图片。失败不会把本机文件路径发到公屏，可用聊天历史重新编辑、重试。
- **只上传、不发公屏**：`/chatimage upload <本地路径>`。例如 `/chatimage upload "/Users/me/Pictures/test image.png"`。
- **上传剪贴板图片**：`/chatimage upload clipboard`。
- **返回引用**：成功消息提供“复制图片引用”和“填入聊天”。引用形式为 `[[CICode,url=mcimage://服务器ID/图片SHA256]]`，可再次发送；也可使用 `/chatimage url mcimage://服务器ID/图片SHA256`。
- 引用仅能在所属服务器取回，不能作为浏览器中的公共图床 URL。其他玩家需要对应客户端模组才能显示图片。

服务器保存的是图片内容，聊天引用不包含上传者的本机路径。图片存于 `<世界目录>/chatimage-images/`，重启后引用仍有效；不要删除或单独更换目录里的 `server-id.txt`。同一内容去重，过期按最近成功上传时间计算。

### 服务端配置

首次启动生成 `config/chatimage-server.json`。修改后重启服务器生效。

```json
{
  "enabled": true,
  "compressionFormat": "webp",
  "compressionLevel": "medium",
  "maxFileBytes": 5242880,
  "maxStorageBytes": 536870912,
  "retentionHours": 168,
  "uploadIntervalSeconds": 5
}
```

| 设置 | 含义 |
| --- | --- |
| `enabled` | 是否启用托管和上传；关闭后仍可使用普通网络图片 |
| `compressionFormat` | `webp`（默认，有损压缩静态图）或 `none`（保留原文件） |
| `compressionLevel` | `low` / `medium` / `high`，分别对应质量 85 / 70 / 45；压缩强度越高，通常越小、画质损失越明显 |
| `maxFileBytes` | 单张上传大小，默认 5 MiB；由服务端配置下发，调整无需编译；不能超过 maxInFlightBytes |
| `maxStorageBytes` | 总存储配额，默认 512 MiB；满额时拒绝新图片 |
| `retentionHours` | 图片保留时间，默认 168 小时；0 表示永久保留 |
| `uploadIntervalSeconds` | 同一玩家开始上传的最小间隔，默认 5 秒 |

静态 PNG/JPEG 默认转为有损 WebP；已有 WebP 默认保留，recompressWebp=true 时重新压缩。压缩结果更大时保留输入。静态图压缩，保留透明度，不缩放尺寸。GIF 和已有 WebP 动图本轮**保留原文件**，不会变成第一帧，也不做动图转码。引用的 SHA-256 对应服务器最终保存的文件，压缩后通常与原文件哈希不同。

服务端校验图片格式、文件大小、帧数和像素预算；单帧至多 1600 万像素，整个动画至多 3200 万像素、256 帧。分块须有序，连续 90 秒没有进展或总传输超过 30 分钟会超时。内存中的传输数据预算 maxInFlightBytes 默认 64 MiB（不含解码与临时副本，可配置），磁盘读写和压缩在独立工作线程执行。

### 自动动画播放

默认按 GIF / WebP 文件的**每帧时长和循环次数**播放，使用实际时间计时，不依赖 Minecraft 渲染 FPS。无限循环保持无限，有限循环结束后停在最后一帧；缺失或为 0 的帧时长按 100 ms 处理。

End 设置中可关闭“按图片时序自动播放”，改用 **1–60 FPS** 手动速度（仍遵循文件循环次数）。设置保存在 `config/chatimage-playback.json`。升级后默认启用自动模式，旧 `gifSpeed` 的“渲染次数”含义不再使用。

### 构建检查与依赖

`./gradlew build` 自动执行存储、压缩、动画元数据和计时回归检查；也可单独运行 `./gradlew transferChecks`。独立服务端、多客户端与 MCP 的验证记录见 [port.3 验证](tests/PORT3-VALIDATION.md)。

WebP 编码使用 [usefulness/webp-imageio](https://github.com/usefulness/webp-imageio) 0.11.0（Apache-2.0，含原生库）；WebP 解码使用 [Glavo/jwebp](https://github.com/Glavo/jwebp) 0.2.0（Apache-2.0，依据该发布版本的 POM 和源码头部）。依赖已随 JAR 内嵌，不要求服主额外安装命令行编码器。编码原生库的平台范围见依赖项目说明；本轮在 macOS arm64 实机验证。

## port.4：更大上传与永久保留

单张上传大小由服务端 maxFileBytes 决定，移除旧版 10/20 MiB 写死限制；port.3 客户端仍限制 10 MiB。
客户端默认仅在超限时降低 WebP 质量，必要时缩小尺寸，原文件不改动；GIF/动态 WebP 超限时提示，不扁平化。
End 设置可关闭“超限自动压缩”。config/chatimage-upload.json 中 maxSourceBytes 默认 64 MiB，限制本地源文件读取及接收图片的内存大小，可配置。
服务端 maxInFlightBytes 必须不小于 maxFileBytes；大图片仍须满足像素/帧数保护限制。
`retentionHours: 0` 表示永久保留，不按时间删除。存储额度满时拒绝新图片，不淘汰已有图片。
原有默认设置不变；可选配置示例：`maxFileBytes: 20971520`、`maxStorageBytes: 2147483648`、`retentionHours: 0`、`uploadIntervalSeconds: 1`。

## port.5：批量传输、磁盘缓存与限速

两端都使用 port.5 时，每批最多连续发送 8×12 KiB，再批量确认；旧版仍使用单块确认。
上传和下载显示进度、两位小数的自适应单位速度，上传结束后显示等待服务端处理。

服务器图片增加磁盘缓存，位于客户端 cachePath 下的 server-images；按完整服务器 ID 与图片哈希区分，读取校验 SHA-256，损坏后回源下载。
重连、重启游戏都可以复用；默认 512 MiB，满额清理最久未使用的客户端缓存，不删除服务器原件。

客户端 config/chatimage-upload.json 配置：

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| transferWindow | 8 | 每批分块数量，1–8，与服务器协商；1 为旧版逐块方式 |
| diskCacheBytes | 536870912 | 磁盘缓存额度，0 禁用缓存 |
| maxSourceBytes | 67108864 | 本地图片读取与下载接收预算 |
| autoCompress | true | 仅对超过服务器限额的静态图预压缩 |

服务端 config/chatimage-server.json 增加 uploadBytesPerSecond、downloadBytesPerSecond：均为每玩家每秒图片数据字节数，默认 0 不限速；非零至少 1024。例如 1048576 表示 1 MiB/s。
限速允许一个批次的初始突发；实际网络还包含 Base64/协议开销，不能当作物理网卡总带宽限制。
配置调整后重启对应端生效。服务器日志记录每次成功上传的输入/保存大小、传输/处理耗时和批次数量。

## port.6：Windows 粘贴修复与同条消息多图

修复 Windows 剪贴板把反斜杠路径直接拼进 file:/// 导致 URI 解析失败的问题，使用正确转义的文件 URI，并兼容旧版 Windows 路径。
拖入/粘贴使用进程内短附件标记，避免长本地路径先触及聊天输入长度限制。

一次拖入多张图片，或在聊天里继续粘贴图片，回车后依次上传；全部成功才发送一条合并消息。上传间隔按服务端协商执行；旧服务端使用保守 5 秒间隔。
原版聊天仍有 256 字符限制，完整服务器图片引用通常最多两张，文字/名称会占用额度。超过限制会在上传前提示拆分，不截断消息。
Windows 原生截图剪贴板最终操作需在 Windows 客户端复测；测试已覆盖其图片转 PNG/URI 分支及文件路径规范化。
