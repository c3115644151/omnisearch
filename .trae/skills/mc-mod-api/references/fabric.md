# Fabric API 参考与查询方法

Fabric 与 Forge/NeoForge 是完全不同的架构。没有事件总线，使用回调接口；没有 DeferredRegister，直接用 Registry。

## 一手文档源

| 来源 | URL | 用途 | 可靠性 |
|------|-----|------|--------|
| 官方文档 | https://fabricmc.net/develop/ | API 参考、版本查询 | ⭐⭐⭐⭐ |
| Wiki | https://wiki.fabricmc.net/ | 教程、设置指南 | ⭐⭐⭐ |
| GitHub 源码 | https://github.com/FabricMC/fabric | Fabric API 实现 | ⭐⭐⭐⭐⭐ |
| 模板项目 | https://github.com/FabricMC/fabric-example-mod | 项目模板 | ⭐⭐⭐⭐ |
| Loom 文档 | Fabric Wiki 的 Loom 页面 | 构建配置 | ⭐⭐⭐ |

## GitHub 源码查询命令

### 搜索类
```bash
# Fabric API 是多模块项目，搜索时注意路径
gh api search/code -q "ItemGroupEvents repo:FabricMC/fabric path:fabric-item-group-api"
```

### 读取源码
```bash
# Fabric API 按模块组织，路径较深
gh api "repos/FabricMC/fabric/contents/fabric-api-base/src/main/java/net/fabricmc/fabric/api" | jq -r '.[].name'
```

### 列出模块
```bash
# Fabric API 的每个功能是独立模块
gh api "repos/FabricMC/fabric/contents/?ref=1.21.1" | jq -r '.[] | select(.type=="dir") | select(.name | startswith("fabric-")) | .name'
```

## 关键包与类

### 入口点
- `net.fabricmc.api.ModInitializer` — 模组入口接口（`onInitialize()`）
- `net.fabricmc.api.ClientModInitializer` — 客户端入口（`onInitializeClient()`）
- **注意**: Fabric 没有 `@Mod` 注解，通过 `fabric.mod.json` 的 `entrypoints` 配置

### 注册
- `net.minecraft.core.Registry` — 直接使用原版 Registry
- `net.minecraft.core.registries.BuiltInRegistries` — 内建注册表
- **没有** DeferredRegister，直接调用 `Registry.register()`

### 事件/回调
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents` — 服务端 tick
- `net.fabricmc.fabric.api.event.lifecycle.v1.ClientTickEvents` — 客户端 tick
- `net.fabricmc.fabric.api.event.player.UseBlockCallback` — 方块交互
- `net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents` — 连接事件
- **注意**: Fabric 使用回调接口，不是事件总线。每个事件是独立的接口实例。

### 网络
- `net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking` — 服务端网络
- `net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking` — 客户端网络
- `net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry` — 载荷类型注册
- **1.21+**: 使用 `CustomPayload` 接口，类似 NeoForge

### 客户端/GUI
- `net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback` — HUD 渲染（已废弃，用 HudElementRegistry）
- `net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry` — HUD 元素注册
- `net.minecraft.client.gui.screens.Screen` — 基础 Screen 类（相同）

### 创造模式标签页
- `net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents` — 物品组事件
- **1.22+**: 可能变为 `CreativeModeTabEvents`（官方映射迁移）

## Fabric vs Forge/NeoForge 关键差异

| 特性 | Fabric | Forge/NeoForge |
|------|--------|----------------|
| 入口 | `ModInitializer` 接口 | `@Mod` 注解 |
| 配置 | `fabric.mod.json` | `neoforge.mods.toml` / `mods.toml` |
| 注册 | `Registry.register()` | `DeferredRegister` |
| 事件 | 回调接口 | 事件总线 |
| 网络 | `ServerPlayNetworking` | `SimpleChannel` / `CustomPacketPayload` |
| Gradle 插件 | `fabric-loom` | `moddev` / `neogradle` |
| 映射 | Yarn (默认) / 官方 | 官方 (Mojang) |
| Mixin | 原生支持 | 原生支持 |

## 版本说明

### 1.21+
- 官方映射迁移：Fabric API 名称更新为匹配官方名称
  - 例：`ItemGroupEvents` → `CreativeModeTabEvents`
- `HudRenderCallback` 废弃，用 `HudElementRegistry` 替代
- `fabric` mod ID 废弃（已废弃 3 年），改用 `fabric-api`

### 1.20.5+
- Java 21 要求
- 数据组件系统（与 NeoForge 共享）

## Loom 构建配置

```groovy
plugins {
    id 'fabric-loom' version '1.15-SNAPSHOT'
}

dependencies {
    minecraft "com.mojang:minecraft:1.21.1"
    mappings "net.fabricmc:yarn:1.21.1+build.1:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.14"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.119.3+1.21.1"
}
```

### 版本查询
- Loader 版本: https://fabricmc.net/develop/
- Fabric API 版本: https://fabricmc.net/develop/ (选择 MC 版本后显示)
- Loom 版本: https://fabricmc.net/develop/

## 常用查询示例

### 查找 Fabric API 模块
```bash
# 列出所有 Fabric API 模块
gh api "repos/FabricMC/fabric/contents/?ref=1.21.1" | jq -r '.[] | select(.name | startswith("fabric-")) | .name'
```

### 查找特定回调/事件
```bash
# 搜索回调接口
gh api search/code -q "UseBlockCallback repo:FabricMC/fabric"
```

### 生成 Minecraft 源码
```bash
# Fabric 项目中运行
./gradlew genSources
```
