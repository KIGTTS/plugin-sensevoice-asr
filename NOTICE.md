# SenseVoice 插件来源与依赖

离线识别器的配置、文件模型路径加载与流释放流程移植并重写自 LHT02/KIGTTS 的 `android-app/app/src/main/java/com/kgtts/app/audio/Engines.kt` / `AsrEngine`。

本插件源码按 GPL-3.0-only 提供，许可证见 LICENSE；主程序不包含这些实现或依赖。新增 PCM16 校验、可取消会话、插件专属模型导入/下载、端点分句与资源清理。基础端点检测使用音量阈值，是可替换的插件实现；原版降噪、声纹、AEC 与说话人分离不混入主程序。

- sherpa-onnx：1.12.38-noort，沿用原工程 AAR，Apache-2.0，来源 https://github.com/k2-fsa/sherpa-onnx 。该 AAR 不含 ONNX Runtime。
- ONNX Runtime Android：1.24.3，Maven `com.microsoft.onnxruntime:onnxruntime-android`，MIT，来源 https://github.com/microsoft/onnxruntime 。
- 模型不随包分发。模型许可取决于模型发布者，请保留资源包附带的许可证与 NOTICE。

原始二进制 AAR 的 SHA-256 记录在依赖校验文件。第三方许可证位于 licenses/，打包时复制入 assets/licenses/。
