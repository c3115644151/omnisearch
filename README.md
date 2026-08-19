# Omnisearch

**在游戏内直接搜索和浏览 [mcmod.cn](https://www.mcmod.cn) —— 中文最大的 Minecraft 百科站点。**

告别反复 Alt-Tab。Omnisearch 把 mcmod.cn 的完整搜索与条目数据库直接搬进你的 Minecraft 客户端：搜索任意模组、物品、资料页；以富文本形式浏览带图片、表格、图标和排版的百科条目；长按一个键即可查看任意模组物品的资料页。

<p align="center">
  <a href="https://www.mcmod.cn/class/23059.html">MC百科</a> ·
  <a href="https://modrinth.com/mod/omnisearch">Modrinth</a> ·
  <a href="https://www.curseforge.com/minecraft/mc-mods/omnisearch">CurseForge</a> ·
  <a href="https://github.com/c3115644151/omnisearch/releases">GitHub Releases</a>
</p>

---

## 功能特性

- **游戏内搜索** —— 按 **TAB** 打开搜索窗口，检索 mcmod.cn 的综合索引（filter=3）。结果**自动加载全部页面**，不会漏掉任何一页。
- **原生渲染的资料页** —— 物品/模组详情页以原生方式渲染：
  - 图片（真实尺寸、居中、懒加载）与图片注释
  - 富文本：颜色、加粗/斜体/下划线/删除线、首行缩进、文本对齐
  - 标题层级、列表、分隔线、`fieldset` 区块，以及 **表格**（支持 colspan、行列分隔线）
  - **原生 Minecraft 图标**（护甲、状态效果等，含满格与半格变体）
  - 可点击的外部链接（浏览器打开）
- **悬停查询** —— 在任意界面中，对着物品**长按 TAB** 即可直接打开它的 mcmod.cn 资料页。自动应用来源模组筛选，即使其它模组有同名物品也能精准定位。
- **模组筛选标签** —— 点击结果的来源模组名即可按模组筛选；随时点击 ✕ 清除。
- **离线友好缓存** —— 资料页与搜索结果在本地磁盘缓存（带 TTL），再次访问即时加载，自动处理限流。

## 环境要求

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **加载器** | NeoForge 21.0.167+ |
| **Java** | 21+ |

## 安装

1. 为 Minecraft 1.21.1 安装 [NeoForge](https://neoforged.net/)。
2. 从 [Modrinth](https://modrinth.com/mod/omnisearch)、[CurseForge](https://www.curseforge.com/minecraft/mc-mods/omnisearch) 或 [GitHub Releases](https://github.com/c3115644151/omnisearch/releases) 下载 `omnisearch-2.0.0.jar`。
3. 将 JAR 放入你的 `mods/` 文件夹。
4. 启动游戏。

## 使用方式

| 操作 | 按键 | 位置 |
|---|---|---|
| 打开搜索窗口 | **TAB** | 游戏内 |
| 悬停查询（长按） | **TAB**（按住约 1 秒） | 任意带物品提示的界面 |
| 关闭窗口 | **ESC** | 搜索窗口 |
| 清空缓存并刷新 | **F6** | 搜索窗口 |

**一键查询物品：** 打开任意背包/界面，鼠标悬停物品，按住 **TAB** 直到进度环填满。Omnisearch 自动解析物品来源模组并打开其 mcmod.cn 资料页。

## 从源码构建

```bash
./gradlew :1.21.1:build
```

产物 JAR 位于 `versions/1.21.1/build/libs/omnisearch-2.0.0.jar`。

**运行开发客户端：**

```bash
./gradlew :1.21.1:runClient
```

项目使用 [Stonecutter](https://github.com/kikugie/stonecutter) 实现单代码库多版本支持。当前仅启用 1.21.1 / NeoForge；可在 `settings.gradle.kts` 与 `versions/` 中添加更多版本。

## 测试

```bash
./gradlew :1.21.1:test
```

测试套件覆盖解析器（mcmod.cn HTML → 文档模型）、纯 Java 布局引擎、渲染组件、缓存与状态 reducer。

## 文档

深入的设计文档位于 [`docs/`](docs/)：
- [`01_overview.md`](docs/01_overview.md) — 高层设计
- [`02_architecture.md`](docs/02_architecture.md) — 模块划分与数据流
- [`03_data_model.md`](docs/03_data_model.md) — 文档模型
- [`06_data_source.md`](docs/06_data_source.md) — mcmod.cn 集成
- [`07_multiversion.md`](docs/07_multiversion.md) — Stonecutter 配置

## 版本说明

这是原版 Omnisearch（此前为 1.0.x）的**彻底重写**。原版因 mcmod.cn 升级反爬机制而完全失效，2.0.0 从零重建了整条管线（解析器、渲染器、悬停交互、分页加载）。详见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证

[MIT](LICENSE) © 2025 凝筝 (cy311)
