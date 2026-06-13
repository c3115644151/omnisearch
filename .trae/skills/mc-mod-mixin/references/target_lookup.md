# Mixin 目标查找方法

写 Mixin 之前必须确认目标类和方法在源码中存在。以下是查找方法。

## 方法 1: 在项目中生成源码

```bash
# NeoForge/Forge 项目
./gradlew genSources

# Fabric 项目
./gradlew genSources
```

生成后可在 IDE 中直接搜索和浏览 Minecraft 源码。

## 方法 2: 通过 GitHub API 查询

### 搜索类
```bash
# 在 NeoForge 仓库搜索
gh api search/code -q "class Screen repo:neoforged/NeoForge path:projects/neoforge"

# 在 Forge 仓库搜索
gh api search/code -q "class Screen repo:MinecraftForge/MinecraftForge path:src/main"
```

### 读取特定类源码
```bash
# NeoForge（源码在 projects/neoforge 子目录下）
gh api "repos/neoforged/NeoForge/contents/projects/neoforge/src/main/java/net/minecraft/client/gui/screens/Screen.java?ref=1.21.1" | jq -r '.content' | base64 -d

# Forge（源码直接在 src/main 下）
gh api "repos/MinecraftForge/MinecraftForge/contents/src/main/java/net/minecraft/client/gui/screens/Screen.java?ref=1.20.x" | jq -r '.content' | base64 -d
```

**注意**: Minecraft 源码在 NeoForge 仓库中的路径是 `projects/neoforge/src/main/java/`，不是 `src/main/java/`。

### 搜索特定方法
```bash
# 搜索方法定义
gh api search/code -q "void render repo:neoforged/NeoForge path:projects/neoforge filename:Screen.java"
```

## 方法 3: 通过 DeepWiki

DeepWiki 自动从源码生成文档，适合快速浏览类结构：

- NeoForge: https://deepwiki.com/neoforged/NeoForge
- Minecraft (vanilla): DeepWiki 可能没有直接的 vanilla 索引

## 方法 4: 使用 Aldak Javadoc（NeoForge 专用）

https://aldak.netlify.app/ 提供社区维护的 NeoForge Javadoc。

覆盖版本：1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10

适合查方法签名、参数列表、返回值类型。

## 方法描述符格式

Mixin 的 `@At("INVOKE")` 中的 target 使用 JVM 内部格式：

```
L包/路径/类名;方法名(参数类型)返回类型
```

### 常见类型缩写

| Java 类型 | 描述符 |
|-----------|--------|
| `int` | `I` |
| `float` | `F` |
| `double` | `D` |
| `long` | `J` |
| `boolean` | `Z` |
| `byte` | `B` |
| `char` | `C` |
| `short` | `S` |
| `void` | `V` |
| `String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| 自定义类 | `Lcom/example/Class;` |

### 示例

```java
// Java 方法签名
public int draw(PoseStack poseStack, String text, float x, float y, int color)

// 对应 Mixin 描述符
target = "Lnet/minecraft/client/gui/Font;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I"
```

### 如何生成描述符

最可靠的方式：在生成的 Minecraft 源码中找到方法签名，然后手动转换为描述符。

**不要**凭记忆写描述符。方法的参数类型和顺序容易记错。

## 常见目标类路径

### 客户端相关
- `net.minecraft.client.Minecraft` — 游戏主类
- `net.minecraft.client.gui.screens.Screen` — 屏幕基类
- `net.minecraft.client.gui.Gui` — HUD 渲染
- `net.minecraft.client.renderer.GameRenderer` — 游戏渲染器
- `net.minecraft.client.player.LocalPlayer` — 本地玩家

### 世界/实体相关
- `net.minecraft.world.level.Level` — 世界
- `net.minecraft.world.entity.Entity` — 实体基类
- `net.minecraft.world.entity.player.Player` — 玩家
- `net.minecraft.world.item.ItemStack` — 物品栈

### 渲染相关
- `net.minecraft.client.renderer.entity.EntityRenderer` — 实体渲染器
- `net.minecraft.client.renderer.blockentity.BlockEntityRenderer` — 方块实体渲染器
- `com.mojang.blaze3d.vertex.PoseStack` — 矩阵栈

**注意**: 以上路径使用官方(Mojang)映射。Fabric 默认使用 Yarn 映射时类名可能不同。
