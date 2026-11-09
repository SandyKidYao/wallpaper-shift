# 壁纸时移 (Wallpaper Shift)

按一天中的时间，自动切换手机的锁屏和桌面壁纸。用户创建**壁纸集**，给每张图设一个开始时间。启用后，到点自动换图：锁屏用原图，桌面用模糊后的图。

## 目录

```
wallpaper-shift/
├── design/            跨平台设计资料：规范、图标源文件、设计 token（真机截图只在本地）
├── shared/            各平台共用的资源
│   └── demo-assets/   示例壁纸集「一日光影」（随 app 安装，首次启动时建好并复制到相册）
├── android/           Android 应用（Gradle 工程，Kotlin + Jetpack Compose）
└── tmp/               临时开发文件，不纳入版本管理
```

新增平台时，在根目录加一个同级目录（例如 `ios/`、`harmony/`），并遵循 `design/` 里的规范。

## 各平台状态

| 平台 | 目录 | 状态 |
|---|---|---|
| Android | [android/](android/) | 0.1.0 正式版，2026-10-20 在 vivo V2454A（Android 16）真机验收 |
| 其他 | — | 未开始。动手前先看 [design/README.md](design/README.md) 第 10 节，确认平台能力 |

## 从哪里开始看

- **做新平台、改界面**：[design/README.md](design/README.md)，包括产品概念、图标、颜色、组件、行为规则和文案。机器可读的数值在 [design/tokens.json](design/tokens.json)。
- **构建 Android 版、了解实现与厂商限制**：[android/README.md](android/README.md)。

## 约定

- `tmp/` 用来放临时的开发文件（示例素材原图、界面评审页等），已写进 `.gitignore`，不会被提交。需要长期保留、被构建或文档引用的东西，放进 `design/` 或 `shared/`。
- 每个平台目录自成一个可以独立构建的工程。各平台共用的只有 `design/` 和 `shared/`。
