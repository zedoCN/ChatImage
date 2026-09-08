# port.6 验证

- Java 25 build 通过 63 项回归；新增旧 Windows drive URI、中文/空格/百分号/逗号/方括号路径、短附件映射、两图替换、顺序/文本保留和三图长度拒绝。
- 保留原 HMCL 33 个模组的隔离客户端，通过 MCP 调用 WindowsPasteCompat 的截图转文件分支（BufferedImage 输入，无系统剪贴板修改），得到正确 file URI。
- 截图附件与另一 PNG 合并后输入长 155 字符；服务器日志分别保存 76->72 字节及 788100->171476 字节图片，遵守 1 秒间隔，最终仅发送一条包含两个 CICode 的聊天消息。
- 客户端日志显示同一条 [Image] [Image]。
- Windows 原生截图/剪贴板完整链路未在 Windows 机器复测，不以 macOS 测试替代 Windows 验收。
