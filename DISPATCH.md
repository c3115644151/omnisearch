# Omnisearch v2 — 团队调度文档

> **日期**: 2026-06-13
> **项目根目录**: c:\Users\32800\Desktop\omnisearch
> **本轮目标**: 实现 keybinds + client/event 层（按键绑定 + 客户端入口 + 工具提示事件），打通 MC 事件到搜索管线的入口

---

## 一、项目现状

### 已完成部分
- 纯 Java 数据层完整：data.model, search, data.parser, data.client, data.source, data.repository
- 完整数据管线：HttpClient → Parser → DataSource → CacheLayer → SearchRepository
- CAPTCHA 处理：检测/解析/异常传播
- 287 测试，全部通过

### 存在问题
- 无 MC 事件入口——游戏内无法触发搜索
- 无按键绑定——无法打开搜索界面
- 无工具提示事件——无法悬停物品长按搜索

### 技术债
- CSS 选择器基于旧代码推导，待用当前 mcmod.cn 页面验证

---

## 二、不可变约束

### 设计约束
1. **Screen ≤ ~80 行** — 只做布局和事件分发
2. **绝不 @Overwrite** — 除非注释说明理由
3. **NeoForge API 调用必须验证签名** — 注释标注 `// verified: [source] [date]`
4. **Client 类只在本端注册** — 通过 FMLClientSetupEvent 或客户端事件

### 测试纪律
5. MC 依赖层测试需要游戏环境，以编译验证为主

---

## 三、依赖图

```
Agent G: keybinds + client/event（无纯 Java 层之外的依赖）
  └── keybinds/KeyBinds.java
  └── client/ClientEntryPoint.java
  └── client/event/TooltipEventHandler.java

全部可并行实现。
```

---

## 四、团队定义

### Agent G: keybinds + client/event
**代号**: mc-entrypoint
**文件边界**:
- `src/main/java/com/cy311/omnisearch/keybinds/KeyBinds.java`
- `src/main/java/com/cy311/omnisearch/client/ClientEntryPoint.java`
- `src/main/java/com/cy311/omnisearch/client/event/TooltipEventHandler.java`
**禁止触碰**: data/ search/ 下的任何文件（只引用 search 状态类型）
**依赖**: data.model, search（已完成）

#### KeyBinds.java
注册搜索按键绑定（默认 TAB 键），提供静态访问方法供其他模块检查按键状态。

```java
public class KeyBinds {
    public static final String CATEGORY = "key.categories.omnisearch";
    public static KeyMapping openSearch;
    
    public static void register(RegisterKeyMappingsEvent event);
}
```

**NeoForge 1.21.1 API**（需验证后使用）：
- `net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent`
- `net.minecraft.client.KeyMapping` — 构造参数：name, key, category

#### ClientEntryPoint.java
NeoForge 客户端入口，注册事件处理器。

```java
public class ClientEntryPoint {
    public static void init(IEventBus modEventBus);
}
```

注册：
1. `RegisterKeyMappingsEvent` → 转发到 KeyBinds.register()
2. `RegisterGuiLayersEvent` → 注册搜索 overlay（后续 Wave 实现）
3. 客户端事件订阅 → 转发到 TooltipEventHandler

#### TooltipEventHandler.java
处理物品工具提示的长按事件。当玩家在物品上长按 TAB 时触发搜索。

从旧代码 ClientEvents.java 的逻辑：TAB 键长按检测，如果有悬停物品则提取物品名、打开搜索界面。

```java
public class TooltipEventHandler {
    public static void onClientTick(ClientTickEvent.Pre event);
    // 检测 TAB 长按 → 提取悬停物品名 → 触发搜索
}
```

---

## 五、进度追踪

| Agent | 状态 | 完成说明 |
|-------|------|---------|
| G: mc-entrypoint | ✅已完成 | KeyBinds(TAB)+ClientEntryPoint+TooltipEventHandler(TAB长按)+OmnisearchMod客户端条件分发 |

---

## 历史记录

### 2026-06-13 第1-3b轮：纯 Java 数据层完成（287 测试）

### 2026-06-14 第4轮：keybinds + client/event 入口层
- **结果**：✅ 完成
- **产出**：
  - KeyBinds: 默认 TAB 键绑定 + RegisterKeyMappingsEvent 注册
  - ClientEntryPoint: 模组事件总线客户端入口
  - TooltipEventHandler: 按键打开搜索 + TAB 1秒长按物品触发搜索
  - OmnisearchMod: 客户端条件分发 `FMLEnvironment.dist == Dist.CLIENT`
- **验证**：所有 NeoForge API 签名已通过 GitHub 源码验证（`// verified:` 注释）
