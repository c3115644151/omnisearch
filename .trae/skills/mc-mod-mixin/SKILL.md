---
name: mc-mod-mixin
description: Minecraft mod Mixin开发标准方法。当需要编写@Mixin、查找Mixin目标、配置mixin JSON、选择注入类型(@Inject/@Redirect/@Overwrite等)、处理Access Transformer、或排查Mixin编译/运行错误时使用。解决agent凭记忆写Mixin导致目标错误、描述符不匹配、注入点选择不当的问题。
---

# MC Mod Mixin 开发

Mixin 是 MC mod 开发中最容易出错的技术。目标类名、方法描述符、注入点选择，任何一个错误都会导致崩溃或无效果。本技能强制你在写 Mixin 前验证目标。

## 核心原则

**Mixin 目标必须在源码中确认存在。** 凭记忆写的目标类名和方法签名大概率对不上。

## Mixin 开发流程

### Step 1: 确定目标

明确你要修改什么：
- 目标类（全限定名）
- 目标方法（名称 + 描述符）
- 修改意图（注入逻辑、重定向调用、修改返回值等）

### Step 2: 验证目标存在

**必须**在 Minecraft 源码中确认目标类和方法存在且签名正确：

```bash
# 1. 确认目标类存在（NeoForge 项目）
# 在项目中生成源码后搜索
grep -r "class Screen" projects/neoforge/src/main/java/net/minecraft/client/gui/screens/

# 2. 或通过 GitHub 搜索
gh api search/code -q "class Screen repo:neoforged/NeoForge path:projects/neoforge"

# 3. 读取目标类源码确认方法签名
```

### Step 3: 选择注入类型

读取 `references/injection_types.md` 选择正确的注入类型。

### Step 4: 编写 Mixin

遵循以下规则：
- Mixin 类必须 `abstract`，不能实例化
- 目标方法用 `@Shadow` 或 `@Accessor` 访问
- 使用 `@Inject` 等注解时，`method` 参数必须匹配方法名
- `at = @At("HEAD")` 等注入点必须理解其含义

### Step 5: 配置 mixin.json

读取 `references/mixin_config.md` 确保 JSON 格式正确。

### Step 6: 验证

- 编译通过不代表 Mixin 生效
- 运行时检查 Mixin 是否被加载（日志中搜索 `mixin`）
- 使用 `@Debug` 注解或日志确认注入点命中

## 何时读取详细参考

| 场景 | 读取文件 |
|------|----------|
| 选择注入类型 | `references/injection_types.md` |
| 配置 mixin.json | `references/mixin_config.md` |
| 查找 Mixin 目标 | `references/target_lookup.md` |
| 排查 Mixin 错误 | `references/common_errors.md` |

## 高频错误速查

### 目标类名用错映射
```
❌ @Mixin(TargetClass.class)  // 你猜测的类名
✅ @Mixin(TargetClass.class)  // 在源码中确认过的类名
```
Forge/NeoForge 用官方映射，Fabric 默认用 Yarn。类名可能完全不同。

### 方法描述符错误
```
❌ method = "render"  // 可能有多个重载
✅ method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V"  // 精确描述符
```
有方法重载时，必须指定完整描述符。

### 在 Mixin 中直接访问私有字段
```
❌ this.privateField = value;  // Mixin 不自动获得访问权限
✅ @Shadow private Type privateField;  // 使用 @Shadow 声明
```

### @Overwrite 滥用
```
❌ @Overwrite  // 几乎永远不要用，会与其他 mod 冲突
✅ @Inject(method = "...", at = @At("HEAD"), cancellable = true)  // 用注入替代
```
`@Overwrite` 完全替换原方法，破坏兼容性。只在绝对必要时使用。

## 映射系统注意

| 加载器 | 默认映射 | Mixin 目标名 |
|--------|----------|-------------|
| NeoForge | 官方 (Mojang) | 官方名 |
| Forge | 官方 (Mojang) | 官方名 |
| Fabric | Yarn（默认）/ 官方（可配置） | 取决于配置 |

Fabric 项目如果配置了官方映射，Mixin 目标也用官方名。检查 `build.gradle` 中的 mappings 配置。
