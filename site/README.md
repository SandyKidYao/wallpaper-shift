# 壁纸时移 · 产品页

原生 HTML/CSS/JavaScript 静态产品页，与 Android 工程独立。

线上地址：<https://sandykidyao.github.io/wallpaper-shift/>

## 本地预览

从本目录启动：

```sh
python3 -m http.server 4173 --bind 127.0.0.1
```

打开 `http://127.0.0.1:4173`。页面无需运行时依赖。

## 发布

仓库使用 `.github/workflows/pages.yml` 部署到 GitHub Pages。推送 `main` 上的 `site/**` 或该工作流变更时会自动发布，也可手动运行 Product page 工作流。

```sh
python3 site/build.py
```

在仓库根目录运行上述命令，产物位于 `site/dist/`。构建只收集页面实际引用的图片、字体、脚本和 APK，并加入字体许可；不发布设计评审、原始生成记录、备份或 node_modules。公开图片保留简短来源记录，移除自由文本/EXIF 中的本地路径，像素与色彩配置保持不变。

所有资源使用相对地址，支持 `/wallpaper-shift/` 项目路径。`dist/` 不提交；Actions 负责构建并上传 Pages 产物。

## 设计依据

- `PRODUCT.md`：产品定位、真实能力和已确认约束。
- `DESIGN.md`：网页设计系统。
- `.impeccable/config.json`、`.impeccable/surfaces/`、`.impeccable/design.json`：随页面代码维护的设计记录。
- 评审截图与效果图保留在本地，不纳入公开仓库。

演示人物采用用户确认的 Leo 壁纸，文案围绕自动切换、锁屏/桌面效果、日出日落和自定义壁纸集展开。

`downloads/wallpaper-shift-android.apk` 为源码提交 `499f005` 构建的已验证签名正式版，当前版本 0.1.0。应用更新时同步替换此文件。
