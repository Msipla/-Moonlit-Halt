# 红石音乐部署器

Minecraft Java 1.20.1 单人创造模式原型。整曲、开头试听和音色展示都是可以拆改的原版音符盒电路。放置后用木按钮演奏，存档中的电路不依赖 Fabric、命令方块、数据包或资源包。

## 直接试玩

1. 解压 [redstone-music-1.20.1-demo.zip](artifacts/redstone-music-1.20.1-demo.zip)。在 Minecraft 1.20.1 安装 Fabric Loader 0.17.2 和 Fabric API 0.92.6+1.20.1，再把压缩包里的 JAR 放进 `mods/`。
2. 把示例的 `music.nbt` 与 `music.json` 同时放进当前游戏安装的 `config/redstone-music/`。在单人创造世界空手潜行右键铁砧，选工程，改名并生成工程道具。
3. 手持道具瞄准放置起点。蓝线标出实际占地，绿线标出示例中的音符盒。工程随水平朝向旋转。从空地边缘对地面右键放置。示例工程宽 24 格、高 8 格、深 47 格。
4. 三组木按钮分别演奏整曲、约 5 秒试听和各音色依次展示。对着空气右键工程道具可看工程名、原版音色和资源包说明。普通右键铁砧保留原版用法。

示例使用原版音色。外部试听 WAV 用本机 Minecraft 音源渲染，能检查音色、音高和和弦。游戏内的距离衰减、响度和混响以实际演奏为准。

## 从自己的 WAV 或 MP3 生成工程

阶段 A/B 的原型工具都在 `tools/` 下。一次输入 WAV 或 MP3，脚本写出用于预听的临时 MIDI，用户可以直接继续导出，手工处理 MIDI 是可选步骤。开发测试采用 Python 3.12、FFmpeg、PyTorch/Torchaudio 2.8、Transformers 4.45.1、Basic Pitch ONNX 和 CPU。GPU 路径和 Windows 安装步骤尚未现场验证。以下命令从本目录运行，尖括号代指本机路径。

1. Python 环境安装 `demucs==4.0.1`、`torch==2.8.0`、`torchaudio==2.8.0`、`torchvision==0.23.0`、`transformers==4.45.1`、`onnxruntime`、`pretty-midi`、`nbtlib`，以及 `librosa`、`soundfile`、`mir_eval`、`resampy`。从下表的版本安装 `mt3-infer`，并把 Basic Pitch 与 ADTOF PyTorch 源码加入 Python 环境。实验用源码路径，Basic Pitch 仓库根目录与 ADTOF 的 `src` 目录放入 `PYTHONPATH` 即可。
2. 安装 Java 25 JDK 和一套单独的官方 Minecraft Java 26.3，在启动器打开一次让游戏 JAR 和依赖下载齐全。外部导出复用 Midicraft v1.0.0 的发布 JAR 和同版本源码中的 `ExportBuild`，这个 Java 26.3 环境只负责生成结构。部署和演奏使用 Minecraft Java 1.20.1。
3. Demucs 首次运行自动下载 `htdemucs_6s`。YourMT3+ 权重需下载校验，路径与 SHA-256 见下文。ADTOF 权重随其上游仓库提供，使用和分发受上游许可限制。示例压缩包不包含模型权重或任何外部工具程序。

```text
python tools/transcribe.py my-song.wav work
python tools/preview.py work/review.mid work/noteblocks.wav --assets "<1.20.1安装>/assets"
python tools/build_music.py work/review.mid output --title "我的歌曲" --midicraft-source "<Midicraft v1.0.0源码>" --midicraft-jar "<Midicraft v1.0.0发布jar>" --minecraft "<Minecraft 26.3安装目录>"
```

预听满意后，把 `output/music.nbt` 与 `output/music.json` 放进导入目录。`arrangement.json` 记录每音符的原始时间、时值、力度、音高、所属音轨和实际音符盒映射，方便定位错音。量化按 20 游戏 ticks/s，每个音同时发声，原版音符盒按音色自身的两八度音域选用最近可用音色或八度。游戏内音符盒只能触发固定包络的一次发声，MIDI 时值与力度保存在中间结果中，电路无法逐音复制。

YourMT3+ 权重根目录由 `MT3_CHECKPOINT_DIR` 设置。把 [官方 Space 权重](https://huggingface.co/spaces/mimbres/YourMT3/resolve/5e66c1ea173a8186e0d20432b841d3180cc015b5/amt/logs/2024/mc13_256_g4_all_v7_mt3f_sqr_rms_moe_wf4_n8k2_silu_rope_rp_b36_nops/checkpoints/last.ckpt) 放在该目录的 `yourmt3/mc13_256_g4_all_v7_mt3f_sqr_rms_moe_wf4_n8k2_silu_rope_rp_b36_nops/last.ckpt`。应核对 SHA-256 为 `ae38e415c79efd5592dcb9b658cdb99ddb11d4c4e1eaa364cab04a052473fc25`，以防只下载到 Git LFS 指针。

已放置的工程可在卸载 Mod 后继续用按钮演奏。也能把单独的示例 NBT 放进原版存档 `generated/music/structures/`，在空地选起点运行 `/place template music:music <x> <y> <z>`。这条导入指令需要作弊或管理员权限，且会覆盖目标位置的方块。Mod 部署会先检查碰撞。

## 来源核验

| 上游与所查版本 | 实际接口、限制和选择 |
|---|---|
| [StemForge `3bdff55`](https://github.com/tsondo/StemForge/tree/3bdff55d8a0aa0963624167f4881805df4736e1a) | 该版本代码 Apache-2.0。可从音频到 Demucs/BS-Roformer 分轨、Basic Pitch 复音 MIDI、ADTOF 鼓谱与分轨预听。GM 可按轨指定，分轨名称和配置只是音色线索。此前 v1.0.0 的发布许可有差异，第三方权重各有独立条件。 |
| [Muse `ff37ba5`](https://github.com/mqtik/muse/tree/ff37ba5f03c60afa15c5f41e62acc4275ffb3f94) | MIT。本地 Tauri 应用，单钢琴用 Demucs/Transkun，多乐器用 YourMT3+ 直接处理混音。可导出带乐器类别和打击乐的 MIDI，但类别粒度有限。复用同一模型的无界面实现，避免把整套桌面工作站连进工具。 |
| [Midicraft v1.0.0](https://github.com/ekra8154/midicraft/tree/32925a2c51840260590f0286732d4cea44519787) | MIT。原生工具支持 MIDI/NBS/内部曲谱、GM 分轨、预听和 Sponge v3/Litematica 导出。使用上游命令行规划器、原有电路布局与 20 游戏 tick/s 交错方案，外部转换器只负责移植经核验的模板子集。上游 JAR 面向 26.3.x。 |
| [Redstone Music Generator `44e3b6f`](https://github.com/lengdongluofeiyu/Redstone-Music-Generator/tree/44e3b6ff66e6c2500e3e0fb5812752a80740b118) | MIT。MIDI→Anvil 区域文件，通道固定映射，忽略 Program Change。超音域直接夹紧，默认 100 ms 栅格。其布局接口可导出 1.20.1 测试模板，但 24 秒参考曲电路长 478 格。项目未使用它的 1.21 存档写入器。 |
| [McMusicMaker `a05e99a`](https://github.com/Cohenjikan/McMusicMaker/tree/a05e99a6c2d6297631ef1a0e531d813f9e1b21d2) | PolyForm Noncommercial 1.0.0。MIDI 解析确实跟踪 Program Change 和鼓类型，含音色展示、资源包、自动构建及 RedPiano 模组路径。其自动构建配置面向 1.21–1.21.10，本项目未复制其代码。 |
| [Demucs 4.0.1](https://github.com/facebookresearch/demucs) · [Basic Pitch `fa5997a`](https://github.com/spotify/basic-pitch/tree/fa5997af0a8210982619003269994a1be25eddf3) | 分别为 MIT 与 Apache-2.0。分离六个音频声部，保留鼓与贝斯供各自转录，其余重组为谐波输入。贝斯的 GM 33 是按声部分配，未识别具体贝斯型号。 |
| [MT3-Infer `2ea7d3e`](https://github.com/openmirlab/mt3-infer/tree/2ea7d3ebbce9ea7bb65110a779e59ac357f2d7e4) · [YourMT3+](https://huggingface.co/mimbres/YourMT3) · [ADTOF PyTorch `85c192e`](https://github.com/xavriley/ADTOF-pytorch/tree/85c192e78f716ea0b111cc8a5ee4a8f6a3a4f8a9) | MT3-Infer 代码 MIT，官方 YourMT3+ 模型卡为 Apache-2.0。ADTOF 的这版 PyTorch 移植无明确代码许可证，模型权重为 CC BY-NC-SA 4.0。ADTOF 仅外部调用，部署包不包含其代码或权重。 |

## 构建与边界

源码用 Java 17 和 Gradle 8.14.3 执行 `gradle build`，输出在 `build/libs/`。游戏插件仅导入配对的稀疏 1.20.1 原版结构与元信息，校验 SHA-256、支持的方块、尺寸、区块、世界高度、实体和方块碰撞。允许模板携带无交互的普通告示牌。资源包元信息可用 `name`、`version`、`url`，HTTPS 链接由原版确认框处理。已安装/已启用状态由玩家自行核对。

原型限单人创造模式、2 分钟内和 2,000 个可部署音符。部署器单次最多 12,000 个写入方块、50,000 个占地格和 96×64×96 外框。超出拒放。超过 350 个音符盒时预览只显示占地。遇到上游不兼容的方块或计划丢音会拒绝导出。尤其新的 `white_wool_slab` 在 1.20.1 中没有同类方块，依赖它的部分吉他编排无法原样移植。未实现自定义样本、资源包自动安装、多人权限、超大工程分批放置和完整撤销。

[测试报告](TEST-REPORT.md)包含逐音实测、转录偏差和可复现样本。
