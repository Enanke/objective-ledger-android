# 启动器图标与显示名称

## 现状

| 项 | 状态 |
|---|---|
| 应用显示名称 | **客观存在**（`app/src/main/res/values/strings.xml` 的 `app_name`；Manifest 的 `application`/`activity` 两处均引用它） |
| 启动器图标 | **原创矢量自适应图标**（无位图、无第三方素材） |
| applicationId / 包名 | 未改动（`com.enanke.awardledger`） |

## 图标资产（全部为文本矢量，可随源码公开）

```
app/src/main/res/
├── drawable/ic_launcher_foreground.xml     # 前景矢量：聊天气泡 + 勾选（108dp 画布，内容在中心 66dp 安全区内）
├── values/colors.xml                       # ic_launcher_background 背景色 #1E3A5F
└── mipmap-anydpi-v26/
    ├── ic_launcher.xml                     # adaptive-icon：background / foreground / monochrome
    └── ic_launcher_round.xml               # 同上
```

自适应图标结构（两个 XML 内容一致）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- **原创性**：前景是纯几何路径（圆角矩形气泡 + 折线勾选），**不含任何第三方图片、商标、角色或字体**；背景是纯色。
- **安全区**：画布 108×108，全部图形落在中心 66×66（坐标 21..87）内，圆形/方形/圆角遮罩都不会裁到主体。
- **单色图层**：`monochrome` 复用同一矢量，Android 13+ 的主题图标可直接着色。

## 自定义

- 改配色：编辑 `values/colors.xml` 的 `ic_launcher_background`。
- 改图形：编辑 `drawable/ic_launcher_foreground.xml` 的 `pathData`（保持坐标都在 21..87 内）。

## 资源核对（安装器 / 桌面显示）

- **名称**：`AndroidManifest.xml` → `<application android:label="@string/app_name">`、`<activity android:label="@string/app_name">` → `app_name = 客观存在`。
- **图标**：`android:icon="@mipmap/ic_launcher"`、`android:roundIcon="@mipmap/ic_launcher_round"` → `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml` → `@drawable/ic_launcher_foreground` + `@color/ic_launcher_background`。
- **API 35** 起 `anydpi-v26` 自适应图标始终生效，无需传统 PNG 兜底。
- 图标是**编译期资源**，不构成运行时路径依赖。

## 遗留（非阻断）

- 旧位图图标（`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher_foreground.png` 与 `ic_launcher_background.png`，共 10 个 PNG）在改为矢量后**已不再被任何资源引用**，并已在 `.gitignore` 中显式排除以免误入库；**建议手动删除文件本身**。
- `tools/make_launcher_icon.ps1` 是旧的位图生成脚本，**已废弃**（执行会用位图覆盖矢量定义），文件中已注明请勿执行。
