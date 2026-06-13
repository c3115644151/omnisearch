# Omnisearch v2 — 多版本构建策略

## 旧方案的问题

7个Git分支各复制一份完整代码，改一个bug要改7次。开发agent可通过 `gh api repos/c3115644151/omnisearch/branches` 查看当前分支列表。

旧代码曾尝试用RenderCompat做运行时反射兼容，但那是补丁不是架构——只在1.21.5+分支存在，且只是反射替换少量API调用，没有系统性地解决多版本问题。RenderCompat源码可通过 `gh api repos/c3115644151/omnisearch/contents/src/main/java/com/cy311/omnisearch/client/gui/RenderCompat.java?ref=1.21.5` 查看。

## 解决方案：Stonecutter（编译期条件替换）

### 已验证结论

| 主张 | 判定 | 验证来源 |
|------|------|----------|
| Stonecutter适合多版本构建 | ✅ | 官方文档 https://stonecraft.meza.gg/docs |
| Architectury适合Omnisearch | ❌ | Forge/NeoForge支持从1.20.5起broken，GitHub issues |
| 独立JAR比Architectury更可靠 | ✅ | Xaero's Minimap每个loader发独立JAR，最成功的纯客户端多平台mod |

### 为什么不用Architectury

1. Omnisearch是纯客户端mod，不需要Architectury的跨loader运行时抽象
2. Architectury的Forge/NeoForge模板从1.20.5起就有问题（Justin Schaaf在GitHub issues报告）
3. 每个loader发独立JAR已被Xaero's Minimap验证可行
4. Stonecutter只解决多版本问题，不引入Architectury的坏账

## Stonecutter工作方式

编译期条件替换——同一份源码，编译时自动选择对应版本的代码片段：

```java
// 同一个文件，不同版本自动替换
public void renderBackground(GuiGraphics g) {
    /*? if >=1.21.5 {*/
    g.blitSprite(BACKGROUND_SPRITE, 0, 0, width, height);
    /*?} else {*/
    g.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0, width, height);
    /*?}*/
}
```

编译后，1.21.5+的JAR只包含blitSprite版本，1.21.4-的JAR只包含blit版本。

## 版本矩阵

| 目标版本 | 加载器 | Stonecutter版本标记 | 备注 |
|----------|--------|---------------------|------|
| 1.20.1 | Forge | `>=1.20.1 <1.21` | 独立分支，Forge API差异大 |
| 1.21.1 | NeoForge | `>=1.21.1 <1.21.2` | 基线版本 |
| 1.21.2 | NeoForge | `>=1.21.2 <1.21.5` | tell→execute等API变更 |
| 1.21.5 | NeoForge | `>=1.21.5 <1.21.8` | +RenderType变更 |
| 1.21.8 | NeoForge | `>=1.21.8 <1.21.10` | 去jarJar |
| 1.21.10 | NeoForge | `>=1.21.10` | 最新 |

## 项目结构

```
omnisearch/
├── build.gradle
├── settings.gradle
├── stonecutter.gradle.kts          # Stonecutter配置
├── src/
│   └── main/
│       ├── java/com/cy311/omnisearch/
│       │   ├── ...                  # 共享代码
│       │   └── compat/
│       │       └── VersionAdapter.java  # 版本适配（编译期替换）
│       └── resources/
├── versions/
│   ├── 1.20.1/                     # Forge版Gradle配置
│   ├── 1.21.1/                     # NeoForge基线
│   ├── 1.21.2/
│   ├── 1.21.5/
│   ├── 1.21.8/
│   └── 1.21.10/
└── gradle/
```

## 已知的API断点（来自旧分支对比）

以下差异是从旧仓库7个分支的源码对比中提取的，开发时需要用Stonecutter条件标记处理：

| 断点 | 影响范围 | Stonecutter条件 | 旧代码中的位置 |
|------|----------|----------------|---------------|
| `tell()` → `execute()` | ClientEvents命令调度 | `>=1.21.2` | ClientEvents.java |
| `RenderType::guiTextured` 变更 | 渲染层 | `>=1.21.5` | HtmlRenderer.java |
| `setPixelRGBA` → `setPixel` | 图片处理 | `>=1.21.2` | ImageManager.java |
| 背景渲染方式变更 | Screen | `>=1.21.5` | OmnisearchScreen.java |
| jarJar依赖打包 | 构建配置 | `>=1.21.5 <1.21.8` | build.gradle |

## Forge 1.20.1的特殊处理

Forge 1.20.1和其他NeoForge版本的API差异远大于NeoForge各版本之间的差异。两个选择：

1. **独立代码库**：Forge 1.20.1在单独的Git分支，共享data/model/search层，只改gui/render/compat层
2. **Stonecutter统一管理**：理论可行但条件标记会非常多，可读性差

倾向选择1，但待开发时根据实际条件标记数量决定。旧仓库的forge-1.20.1分支已做过初始化清理（MDK提升到根目录、删除混入NeoForge代码），可作为Forge版的起点参考。
