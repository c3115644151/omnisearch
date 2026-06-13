---
name: mc-mod-api
description: 标准化的Minecraft mod加载器(NeoForge/Forge/Fabric)API文档查询方法。当开发MC mod、查询mod加载器API、验证API签名、查找注册方法、确定事件总线用法、查询Screen/渲染API、或任何涉及NeoForge/Forge/Fabric代码开发时使用。解决agent默认使用过时训练数据写mod代码的问题。
---

# MC Mod API 查询

你的训练数据中关于 MC mod 加载器的 API 知识大概率已过时或错误。MC mod API 每个小版本都可能破坏性变更，Forge/NeoForge/Fabric 之间命名和用法完全不同。本技能强制你在写任何 mod 代码之前验证 API。

## 核心原则

**不验证就不写代码。** 凭记忆写的 MC mod API 调用，大概率编译不过或运行崩溃。

## 查询流程

### Step 1: 确定查询目标

明确三个维度：
- **加载器**: NeoForge / Forge / Fabric
- **MC 版本**: 如 1.20.1, 1.21.1, 1.21.5 等
- **功能域**: 注册、事件、网络、Screen/渲染、数据组件、Mixin 等

### Step 2: 按优先级查询

按以下顺序查找，找到可靠信息即可停止，不必每层都查：

1. **GitHub 源码**（最可靠，永远当前）
   - 读取 `references/neoforge.md` 或 `references/forge.md` 或 `references/fabric.md` 获取仓库路径和 gh api 查询命令
   - 直接读源码中的类定义、方法签名、注解

2. **官方文档**（概念性指导，覆盖不全但权威）
   - 读取对应 references 文件获取 URL
   - 适合了解架构、设计思路、迁移指南

3. **Javadoc**（方法签名和参数说明）
   - 仅 NeoForge 有社区维护的 Javadoc（aldak.netlify.app）
   - 适合查具体方法参数和返回值

4. **DeepWiki**（自动从源码生成，结构化概览）
   - deepwiki.com 上有 NeoForge 文档索引
   - 适合快速了解模块关系

5. **网络搜索**（最后手段，必须用源码验证结果）
   - 搜索结果质量参差不齐，CSDN 等中文站内容常过时
   - 找到的代码示例必须对照源码确认

### Step 3: 验证

对查到的 API 执行以下检查：
- 确认类/方法在目标 MC 版本中存在（检查对应分支的源码）
- 确认方法签名（参数类型、顺序、返回值）
- 确认包名正确（NeoForge ≠ Forge 包名）
- 在代码注释中标注验证来源，格式：`// verified: [source] [date]`

## 何时读取详细参考

| 场景 | 读取文件 |
|------|----------|
| 开发 NeoForge mod | `references/neoforge.md` |
| 开发 Forge mod | `references/forge.md` |
| 开发 Fabric mod | `references/fabric.md` |
| 遇到跨加载器问题或不确定的差异 | `references/api_gotchas.md` |
| 需要查版本迁移信息 | `references/api_gotchas.md` 的版本迁移部分 |

## 高频场景速查

以下场景是 agent 最容易出错的，必须特别注意：

### 注册
- NeoForge/Forge: `DeferredRegister.create()` — 但参数顺序在 1.21+ 变了
- Fabric: `Registry.register()` — 完全不同的模式
- **必读**: `references/api_gotchas.md` 注册部分

### 事件
- NeoForge: `NeoForge.EVENT_BUS`，不是 `MinecraftForge.EVENT_BUS`
- Forge: `MinecraftForge.EVENT_BUS`
- Fabric: 回调接口，没有事件总线
- **必读**: `references/api_gotchas.md` 事件部分

### Screen/GUI
- 所有加载器都继承 `net.minecraft.client.gui.screens.Screen`
- 但注册方式完全不同
- NeoForge 1.21+ 使用 `RegisterGuiLayersEvent`
- **必读**: 对应加载器的 references 文件中 Screen 部分

### 网络
- NeoForge/Forge 1.21+: `CustomPacketPayload` + `StreamCodec`
- Fabric: `PayloadTypeRegistry` + `ServerPlayNetworking`
- **必读**: 对应加载器的 references 文件中网络部分

## 版本敏感性检查清单

遇到以下版本时，必须额外查迁移指南：
- **1.20.5+**: 数据组件替代物品 NBT，注册 API 签名变更
- **1.21.1+**: NeoForge FML 状态访问方式变更
- **1.21.5+**: 渲染管线重构，BlockEntityRenderer 变三方法
- **1.21.9+**: FML 完全重写，KeyMapping.Category 变 record
- **Forge 1.20.1 vs NeoForge**: 几乎所有包名不同，API 签名有差异
