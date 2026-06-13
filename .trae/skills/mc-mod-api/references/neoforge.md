# NeoForge API 参考与查询方法

## 一手文档源

| 来源 | URL | 用途 | 可靠性 |
|------|-----|------|--------|
| 官方文档 | https://docs.neoforged.net/ | 概念、架构、迁移指南 | ⭐⭐⭐⭐ |
| 社区 Javadoc | https://aldak.netlify.app/ | 方法签名、参数说明 | ⭐⭐⭐⭐ |
| GitHub 源码 | https://github.com/neoforged/NeoForge | 类定义、实现细节 | ⭐⭐⭐⭐⭐ |
| DeepWiki | https://deepwiki.com/neoforged/Documentation | 模块关系、概览 | ⭐⭐⭐ |
| 迁移指南 | https://docs.neoforged.net/primer/docs/ | 版本变更详情 | ⭐⭐⭐⭐⭐ |

## GitHub 源码查询命令

### 搜索类
```bash
# 在 NeoForge 仓库搜索类名
gh api search/code -q "DeferredRegister repo:neoforged/NeoForge path:src/main/java"

# 搜索特定包下的文件
gh api "repos/neoforged/NeoForge/contents/src/main/java/net/neoforged/neoforge" | jq -r '.[].name'
```

### 读取源码文件
```bash
# 读取特定 Java 文件（需 base64 解码）
gh api repos/neoforged/NeoForge/contents/src/main/java/net/neoforged/neoforge/registries/DeferredRegister.java | jq -r '.content' | base64 -d

# 指定分支读取
gh api "repos/neoforged/NeoForge/contents/src/main/java/net/neoforged/neoforge/registries/DeferredRegister.java?ref=1.21.1" | jq -r '.content' | base64 -d
```

### 列出包目录结构
```bash
# 列出 neoforge 包下所有子包
gh api "repos/neoforged/NeoForge/contents/src/main/java/net/neoforged/neoforge" | jq -r '.[] | select(.type=="dir") | .name'
```

## 关键包与类

### 注册系统
- `net.neoforged.neoforge.registries.DeferredRegister` — 延迟注册器
- `net.neoforged.neoforge.registries.NeoForgeRegistries` — NeoForge 特有注册表
- `net.neoforged.neoforge.event.RegisterEvent` — 注册事件

### 事件系统
- `net.neoforged.neoforge.common.NeoForge` — 事件总线入口（`NeoForge.EVENT_BUS`）
- `net.neoforged.bus.api.IEventBus` — 模组事件总线（构造函数注入）
- **注意**: `MinecraftForge.EVENT_BUS` 不存在，那是 Forge 的

### 客户端/GUI
- `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent` — 1.21+ GUI 层注册
- `net.neoforged.neoforge.client.gui.GuiLayer` — GUI 层接口
- `net.minecraft.client.gui.screens.Screen` — 基础 Screen 类
- `net.neoforged.neoforge.client.event.RenderLevelStageEvent` — 世界渲染事件

### 网络
- `net.neoforged.neoforge.network.PacketDistributor` — 包分发器
- `net.neoforged.neoforge.network.CustomPacketPayload` — 自定义包载荷
- `net.minecraft.network.codec.StreamCodec` — 流编解码器

### 数据组件 (1.20.5+)
- `net.neoforged.neoforge.event.ModifyItemComponentsEvent` — 修改物品组件
- `net.minecraft.core.component.DataComponents` — 原版数据组件

### 键绑定
- `net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent` — 注册按键
- `net.minecraft.client.KeyMapping` — 按键映射
- **1.21.9+**: `KeyMapping.Category` 是 record，不再是 String

## 版本关键变更

### 1.21.9+（重大变更）
- **FML 重写**: `FMLLoader.getCurrent()` 取代大部分静态方法
  - `FMLEnvironment.dist` → `FMLEnvironment.getDist()`
  - `FMLEnvironment.production` → `FMLEnvironment.isProduction()`
  - `FMLLoader.getGamePath()` → `FMLLoader.getCurrent().getGameDir()`
- **KeyMapping.Category**: 从 String 变为 record，需通过 `RegisterKeyMappingsEvent.registerCategory()` 注册
- **Transfer API 重写**: 大规模重构

### 1.21.5+
- **渲染管线重构**: BlockEntityRenderer 增加类型参数和三个方法
  - `createRenderState()` / `extractRenderState()` / `render()` 替代旧 `render()`
- **自定义方块轮廓**: 订阅 `ExtractBlockOutlineRenderStateEvent`，旧 `RenderHighlightEvent` 被移除

### 1.21.1+
- 注册 API 参数顺序可能变更
- 数据组件系统成熟

### 1.20.5+
- **数据组件替代 NBT**: 物品数据不再使用 NBT，改用 DataComponents
- `DeferredRegister.create()` 签名变更
- 注册系统整体调整

## Gradle 配置参考

### ModDevGradle (推荐，1.21+)
```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.112-beta'
}
```

### NeoGradle (传统)
```groovy
plugins {
    id 'net.neoforged.gradle' version '7.0.192'
}
```

### gradle.properties 关键字段
```properties
minecraft_version=1.21.1
neoforge_version=21.1.212
mod_id=yourmod
mod_name=Your Mod
mod_version=1.0.0
```

## 常用查询示例

### 查找 Screen 基类方法
```bash
# 方法1: 直接读源码（需要知道路径）
gh api repos/neoforged/NeoForge/contents/projects/neoforge/src/main/java/net/minecraft/client/gui/screens/Screen.java?ref=1.21.1 | jq -r '.content' | base64 -d | head -100

# 方法2: 搜索
gh api search/code -q "class Screen repo:neoforged/NeoForge path:projects/neoforge"
```

### 查找注册示例
```bash
# 读 NeoForge 测试 mod 中的注册示例
gh api "repos/neoforged/NeoForge/contents/tests/src/main/java/net/neoforged/neoforge/debug" | jq -r '.[].name'
```

### 查找事件定义
```bash
# 列出所有事件类
gh api "repos/neoforged/NeoForge/contents/src/main/java/net/neoforged/neoforge/event" | jq -r '.[] | select(.type=="dir") | .name'
```
