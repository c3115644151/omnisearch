# Forge API 参考与查询方法

Forge (MinecraftForge) 是 NeoForge 的前身。1.20.1 是 Forge 最后的活跃版本，1.20.2+ 官方推荐迁移到 NeoForge。

## 一手文档源

| 来源 | URL | 用途 | 可靠性 |
|------|-----|------|--------|
| 官方文档 | https://docs.minecraftforge.net/ | 概念、教程 | ⭐⭐⭐ |
| GitHub 源码 | https://github.com/MinecraftForge/MinecraftForge | 类定义、实现细节 | ⭐⭐⭐⭐⭐ |
| MDK 模板 | https://github.com/MinecraftForge/MinecraftForge/tree/1.20.x/mdk | 项目模板 | ⭐⭐⭐⭐ |

**注意**: Forge 文档质量低于 NeoForge，很多内容需要直接看源码。

## GitHub 源码查询命令

### 搜索类
```bash
gh api search/code -q "DeferredRegister repo:MinecraftForge/MinecraftForge path:src/main/java"
```

### 读取源码
```bash
# Forge 1.20.1 分支
gh api "repos/MinecraftForge/MinecraftForge/contents/src/main/java/net/minecraftforge/registries/DeferredRegister.java?ref=1.20.x" | jq -r '.content' | base64 -d
```

### 列出包结构
```bash
gh api "repos/MinecraftForge/MinecraftForge/contents/src/main/java/net/minecraftforge" | jq -r '.[] | select(.type=="dir") | .name'
```

## 关键包与类

### 注册系统
- `net.minecraftforge.registries.DeferredRegister` — 延迟注册器
- `net.minecraftforge.registries.ForgeRegistries` — Forge 注册表
- `net.minecraftforge.event.RegistryEvent` — 注册事件（注意：不是 RegisterEvent）

### 事件系统
- `net.minecraftforge.common.MinecraftForge` — 事件总线入口（`MinecraftForge.EVENT_BUS`）
- `net.minecraftforge.eventbus.api.IEventBus` — 模组事件总线
- **注意**: 这是 `MinecraftForge.EVENT_BUS`，不是 `NeoForge.EVENT_BUS`

### 客户端/GUI
- `net.minecraftforge.client.event.RenderGameOverlayEvent` — HUD 渲染事件
- `net.minecraft.client.gui.screens.Screen` — 基础 Screen 类（与 NeoForge 相同）
- `net.minecraftforge.client.event.RenderWorldLastEvent` — 世界渲染事件

### 网络
- `net.minecraftforge.network.NetworkRegistry` — 网络通道注册
- `net.minecraftforge.network.simple.SimpleChannel` — 简单网络通道
- **注意**: Forge 网络系统与 NeoForge 1.21+ 完全不同

### 物品 NBT（1.20.1 无数据组件）
- `net.minecraft.nbt.CompoundTag` — NBT 数据
- 1.20.1 的 Forge 仍使用 NBT，没有数据组件系统

## Forge vs NeoForge 关键差异

| 特性 | Forge (1.20.1) | NeoForge (1.21+) |
|------|----------------|-------------------|
| 事件总线 | `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| 注册表 | `ForgeRegistries` | `NeoForgeRegistries` |
| 注册事件 | `RegistryEvent.Register` | `RegisterEvent` |
| 网络包 | `SimpleChannel` | `CustomPacketPayload` + `StreamCodec` |
| 物品数据 | NBT (`CompoundTag`) | DataComponents |
| Gradle 插件 | `net.minecraftforge.gradle` | `net.neoforged.moddev` 或 `net.neoforged.gradle` |
| 包名前缀 | `net.minecraftforge.*` | `net.neoforged.neoforge.*` |
| GUI 注册 | `RenderGameOverlayEvent` | `RegisterGuiLayersEvent` |
| 按键注册 | `RegisterKeyMappingsEvent` | `RegisterKeyMappingsEvent`（相同） |

## 版本说明

### 1.20.1（Forge 最后活跃版本）
- 大多数 Forge mod 仍在此版本
- 无数据组件，使用 NBT
- SimpleChannel 网络
- Gradle: `net.minecraftforge.gradle` 5.1.x+

### 1.20.2+（官方建议迁移 NeoForge）
- Forge 1.20.2+ 开发者极少
- API 大幅变动
- 社区资源稀缺

## 常用查询示例

### 查找 Forge 注册示例
```bash
# 读 MDK 示例代码
gh api "repos/MinecraftForge/MinecraftForge/contents/mdk/src/main/java/com/example/examplemod?ref=1.20.x" | jq -r '.[].name'
```

### 查找 Forge 事件列表
```bash
gh api "repos/MinecraftForge/MinecraftForge/contents/src/main/java/net/minecraftforge/event?ref=1.20.x" | jq -r '.[] | select(.type=="dir") | .name'
```
