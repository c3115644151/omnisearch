# MC Mod API 常见错误与版本迁移

Agent 在写 MC mod 代码时最容易犯的错误，按严重性排序。

## 致命错误（编译失败或运行崩溃）

### 1. 混淆 Forge 和 NeoForge 包名
```
❌ import net.minecraftforge.registries.DeferredRegister;  // Forge 包名
✅ import net.neoforged.neoforge.registries.DeferredRegister;  // NeoForge 包名
```
这两个是不同的类，不能混用。NeoForge 从 Forge 分叉后所有包名都改了。

### 2. 用错事件总线
```
❌ MinecraftForge.EVENT_BUS.addListener(...)  // Forge
✅ NeoForge.EVENT_BUS.addListener(...)        // NeoForge
```
NeoForge 中不存在 `MinecraftForge` 类。

### 3. 1.20.5+ 仍用 NBT 操作物品数据
```
❌ stack.getOrCreateTag().putInt("energy", 100);      // 旧方式
✅ stack.set(DataComponents.CUSTOM_DATA, ...);          // 1.20.5+ 方式
```
1.20.5+ 物品数据系统完全重构。CompoundTag 不再用于物品附加数据。

### 4. DeferredRegister.create() 签名错误
不同版本签名不同，必须查源码确认：
- 旧版: `DeferredRegister.create(Registries.BLOCKS, MODID)`
- 新版: `DeferredRegister.create(Registries.BLOCKS, MODID)` 但内部实现变了
- **必须查目标版本的源码确认**

### 5. 客户端代码在服务端执行
```
❌ // 在 Common 代码中直接引用 Screen 类
✅ // 客户端代码放在 client 子包，通过 @OnlyIn 或代理隔离
```
服务端没有 `net.minecraft.client` 包，直接引用会 ClassNotFoundException。

## 严重错误（功能异常）

### 6. Screen 注册方式错误
- NeoForge 1.21+: 使用 `RegisterGuiLayersEvent` 注册 GUI 层
- Forge: 使用 `RenderGameOverlayEvent` 
- **不能**在 mod 构造函数中直接操作 Screen

### 7. 网络包格式错误
- NeoForge 1.21+: `CustomPacketPayload` + `StreamCodec`
- Forge 1.20.1: `SimpleChannel` + `PacketDistributor`
- Fabric 1.21+: `CustomPayload` + `PayloadTypeRegistry`
- 三者完全不兼容，必须查对应加载器的网络 API

### 8. Mixin 目标使用错误映射
```
❌ @Mixin(targets = "net.minecraft.class_1234")  // Yarn 映射（Fabric 默认）
✅ @Mixin(targets = "net.minecraft.world.level.block.Block")  // 官方映射（Forge/NeoForge）
```
NeoForge/Forge 使用官方(Mojang)映射，Fabric 默认使用 Yarn 映射。如果 Fabric 项目配置了官方映射则用官方名。

### 9. Architectury 在 Forge/NeoForge 1.20.5+ 不可用
Architectury 的 Forge/NeoForge 支持从 1.20.5 起已 broken。不要用 Architectury 做跨平台 mod。
替代方案：Stonecutter 做编译期版本替换 + 独立 JAR。

## 常见错误（代码能跑但不对）

### 10. 事件订阅位置错误
- 模组事件（注册等）→ `IEventBus`（构造函数注入的那个）
- 游戏事件（tick、交互等）→ `NeoForge.EVENT_BUS` / `MinecraftForge.EVENT_BUS`
- 搞混会导致事件不触发

### 11. 忽略 Side 标注
- `@OnlyIn(Dist.CLIENT)` 标注的类/方法在服务端不存在
- 即使你的 mod 是 client-only，也要遵守 side 规则，否则专用服务器会崩溃

### 12. Component 误用
`net.minecraft.network.chat.Component` 仅支持文本内容：
- ✅ 文本、翻译键、样式、点击事件
- ❌ 图片、表格、富文本渲染
- 不要尝试用 Component 替代 HTML 渲染器

## 版本迁移速查

### 1.20.1 → 1.21.1
- Forge → NeoForge 迁移（包名全部替换）
- NBT → DataComponents
- SimpleChannel → CustomPacketPayload
- RegistryEvent → RegisterEvent
- RenderGameOverlayEvent → RegisterGuiLayersEvent

### 1.21.x → 1.21.5
- BlockEntityRenderer 变更（三个方法替代一个）
- RenderHighlightEvent 移除
- 自定义方块轮廓换用 ExtractBlockOutlineRenderStateEvent

### 1.21.x → 1.21.9
- FML 完全重写
  - `FMLEnvironment.dist` → `FMLEnvironment.getDist()`
  - `FMLEnvironment.production` → `FMLEnvironment.isProduction()`
  - `FMLLoader.getGamePath()` → `FMLLoader.getCurrent().getGameDir()`
- KeyMapping.Category 从 String 变为 record
- Transfer API 大规模重构

## CRAAP+SIFT 验证清单

对从网络搜索找到的 MC mod 代码示例，执行以下检查：

| 检查项 | 判定标准 |
|--------|----------|
| **时效性** | 代码是否针对目标 MC 版本？1.20.1 的代码不能直接用于 1.21+ |
| **加载器** | 代码是否针对正确的加载器？Forge 代码不能用于 NeoForge |
| **来源权威性** | 是否来自官方文档、GitHub 源码、或知名 mod 作者？CSDN 转载不可信 |
| **可验证性** | 能否在 GitHub 源码中找到对应的类/方法？找不到就不可用 |
| **交叉验证** | 至少两个独立来源确认？单一博客帖子不足以信任 |
