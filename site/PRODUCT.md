# 壁纸时移 · 产品页

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

已确认使用原生 HTML、CSS 和 JavaScript，放在当前项目仓库的 `site/`。现已获用户明确授权部署到 GitHub Pages：`https://sandykidyao.github.io/wallpaper-shift/`。本地预览继续可用。

## Users

想了解并下载 Android 版壁纸时移的访客，尤其是喜欢让同一场景随一天光线变化的手机壁纸用户。

## Product Purpose

让访客通过真实壁纸演示理解壁纸时移如何按时间自动切换锁屏和桌面壁纸，再找到 Android 下载和使用入口。介绍围绕产品能力、适用方式与用户自己的壁纸集展开，Leo 书房壁纸仅作为演示素材。

## Positioning

把一组壁纸与每日时间表连接起来：锁屏保持清晰、桌面可调模糊，早晨和夜晚可跟随设备提供的日出日落时间。

## Operating Context

Android 是当前已实现的平台。用户安装 APK，创建或导入 ZIP 壁纸集，检查时间表并启用；同一时间只启用一个集。支持壁纸集导入与导出。

## Capabilities and Constraints

默认六时段为黎明 04:00、早晨跟随日出（04:30–09:30，回退 06:00）、正午 11:00、午后 14:00、夜晚跟随日落（15:30–22:00，回退 18:00）、深夜 23:00。有效系统时间越界时取最近边界；不可用时采用图片保存的回退时间。设备支持有差异，不承诺天文级精度。准时切换受 Android 权限及厂商后台省电影响。

## Brand Commitments

名称为「壁纸时移 / Wallpaper Shift」。沿用 `../design/icon/` 的现有产品图标。用户已选定「一间书房，六种光线」的效果图，采用连续六时段光线切片和时间轴。Leo 壁纸仅用于演示，不把产品包装成角色主题应用；角色外观遵循 Leo 设定，保留取书动作与书房构图。Android 原生界面的规范继续由 `../design/README.md` 管理。

## Evidence on Hand

用户已验收的单张基准图与统一派生六时段素材位于 `../output/wallpapers/leo-library-v2-20261111/`；书房原场景参考图来自用户手机；按用户明确指示，把原角色替换为用户 `workspace/leo` 项目原创 Leo。角色依据 `design/persona/PERSONA.md`、官方 `regular-sheet.webp` 与 `hair-clip.png`，采用常规身份特征；根据用户修正，服装沿用原图白色露肩针织衫与花纹短裤，保持裸足；内置图片工具生成。提示词和处理记录在该目录。产品能力依据应用源码、README 和真实构建验证。GitHub 仓库为 https://github.com/SandyKidYao/wallpaper-shift 。无用户数量、评价、奖项或商业定价证据，不虚构。

## Product Principles

- 用实际光线变化解释价值，让用户先体验再阅读。
- 下载、导入、启用的路径明确。
- 如实说明设备限制，不用绝对准时等无法保证的承诺。
- 页面适应手机和桌面，控制项支持键盘与减少动态效果偏好。
