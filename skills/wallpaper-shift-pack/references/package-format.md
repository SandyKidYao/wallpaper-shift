# Wallpaper Shift 导入格式 v1

本技能输出一个普通 UTF-8 ZIP，根目录只有 `manifest.json` 和所列 JPEG 图片，不套外层文件夹。格式来自项目 `design/README.md` §8.9 和 Android `SetPackage.kt`。

```json
{
  "format": "wallpaper-shift.set",
  "version": 1,
  "name": "海边的一天",
  "blur": 10,
  "items": [
    {"file": "01.jpg", "start": "04:00", "label": "黎明"},
    {"file": "02.jpg", "start": "18:00", "label": "夜晚", "anchor": "dark-start"}
  ]
}
```

- `format` 固定为 `wallpaper-shift.set`，`version` 为整数 1。
- `name` 为非空名称；`blur` 为整数 0–25，仅影响桌面，锁屏保持清晰。
- `items` 为 1–24 张图片，`file` 路径相对清单。技能始终显式写出 `start`（24 小时 `HH:mm`）与非空 `label`，开始时间互不重复。无需第一张从 00:00 开始：凌晨沿用上一天最后一个时段。
- 可选 `anchor` 只有 `light-start`（日出）或 `dark-start`（日落），用户明确要求跟随日出日落，或认可包含跟随规则的默认六张方案时添加。`start` 必须保留为回退时间。新版 APP 对有效日出采用 04:30–09:30、有效日落采用 15:30–22:00 的闭区间限制，超出时取最近边界，缺失或无效时使用 `start`；旧版 APP 可能直接使用系统时间。默认六张依次为黎明 04:00、早晨（日出，回退 06:00）、正午 11:00、午后 14:00、夜晚（日落，回退 18:00）、深夜 23:00。安全范围是 APP 规则，无需新增 manifest 字段；自定义其他节点仍需自行检查冲突。支持依赖设备：vivo 可读系统日出日落时间，其他设备可能使用深色模式自定义时间或回退。不要承诺天文级日出日落精度。
- APP 读取限制：单张解压后不超过 64 MiB，图片合计不超过 512 MiB，清单不超过 256 KiB。APP 导入时控制在 1600 万像素内。技能脚本限制目标和输入图片像素不超过 1600 万，统一输出 JPEG 以便跨设备解码；这比 APP 能接受的 JPG、PNG、WebP、HEIC/HEIF、AVIF 范围更窄。
- 1440×3120（9:19.5）是推荐值，不是格式硬性要求。所有时段用同一画布，手机会按屏幕显示壁纸，重要主体需留裁剪余量。
- APP 只导入清单列出的图片；技能不附带单独模糊图、缩略图、参考图或预览拼图。

更新此技能时同时核对上游 `SetPackage` 的格式版本、大小限制与字段；不要把 `design/demo-collections/manifest.json` 当作导入清单，它是设计素材索引。
