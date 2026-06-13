# Mixin 配置文件 mixin.json

## 基本格式

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.yourname.yourmod.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "yourmod.refmap.json",
  "mixins": [
    "server.ServerMixin"
  ],
  "client": [
    "client.ClientMixin"
  ],
  "server": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

## 字段详解

### required
- `true`: Mixin 加载失败时游戏崩溃
- `false`: Mixin 加载失败时跳过（可选功能用 false）

### minVersion
Mixin 版本要求。常用值：
- `"0.8"` — 基本稳定版
- `"0.12"` — 支持 @WrapOperation 和 @ModifyReturnValue

### package
Mixin 类的根包。必须与你实际的 Mixin 类包路径一致。

### compatibilityLevel
Java 版本。MC 1.20.5+ 需要 `JAVA_21`。

### refmap
映射引用映射文件。NeoForge/Forge 自动生成。
- NeoForge/Forge: `"yourmod.refmap.json"`
- Fabric: 不需要此字段（Loom 自动处理）

### mixins / client / server

| 数组 | 加载时机 | 适用场景 |
|------|----------|----------|
| `mixins` | 始终加载 | 通用逻辑、服务端+客户端 |
| `client` | 仅客户端加载 | Screen、渲染、按键等客户端代码 |
| `server` | 仅服务端加载 | 专用服务器逻辑 |

**重要**: 客户端专用 Mixin 必须放在 `client` 数组中。放在 `mixins` 中可能导致专用服务器启动崩溃（如果 Mixin 引用了客户端类）。

### injectors.defaultRequire
- `1`: 注入点必须成功匹配（失败时警告但不崩溃）
- `0`: 注入点可选

## NeoForge/Forge 配置

在 `neoforge.mods.toml` / `mods.toml` 中声明 mixin 配置文件：

```toml
[[mods]]
modId = "yourmod"
# ...
[[accessTransformers]]
file = "META-INF/accesstransformer.cfg"

# Mixin 配置文件在 MOD_ID.mixins.json 中自动识别
```

Mixin JSON 文件命名约定：`MOD_ID.mixins.json`，放在 `src/main/resources/` 目录下。

## Fabric 配置

在 `fabric.mod.json` 中声明：

```json
{
  "mixins": [
    "yourmod.mixins.json"
  ]
}
```

## 多配置文件

大型 mod 可以拆分多个 mixin 配置：

```
resources/
├── yourmod.mixins.json        # 通用
├── yourmod.client.mixins.json # 客户端专用
└── yourmod.server.mixins.json # 服务端专用
```

在 `fabric.mod.json` 或自动识别中列出所有文件。

## 常见配置错误

### 1. package 路径与实际不匹配
```
❌ "package": "com.example.mixin"
✅ "package": "com.example.yourmod.mixin"  // 与 Java 包路径一致
```

### 2. 客户端 Mixin 放错数组
```
❌ "mixins": ["client.ScreenMixin"]  // 会在服务端尝试加载
✅ "client": ["client.ScreenMixin"]  // 只在客户端加载
```

### 3. compatibilityLevel 不匹配
MC 1.20.5+ 用 Java 21：
```
❌ "compatibilityLevel": "JAVA_17"
✅ "compatibilityLevel": "JAVA_21"
```

### 4. Mixin 类名不需要包前缀
```json
{
  "client": [
    "client.ScreenMixin"  // 相对于 package 字段
  ]
}
```
不需要写 `"com.yourmod.mixin.client.ScreenMixin"`，package 前缀自动添加。

## Access Transformer

当 Mixin 需要访问原版私有字段/方法时，可能还需要 Access Transformer。

### 配置文件: `src/main/resources/META-INF/accesstransformer.cfg`

```
# 格式: 访问级别 目标
# 访问级别: public, protected, private → public
# 目标: 类.字段 或 类.方法(描述符)

# 让字段变为 public
public-f net.minecraft.client.gui.screens.Screen.width

# 让方法变为 public
public net.minecraft.client.gui.screens.Screen.render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V
```

### Fabric 用 Access Widener

Fabric 不用 AT，用 Access Widener (.accesswidener)：

```
accessWidener v1 named

accessible class net/minecraft/client/gui/screens/Screen
accessible field net/minecraft/client/gui/screens/Screen width I
accessible method net/minecraft/client/gui/screens/Screen render (Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V
```
