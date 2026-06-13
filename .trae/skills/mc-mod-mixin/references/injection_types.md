# Mixin 注入类型选择指南

## 注入类型概览

| 注入类型 | 用途 | 兼容性 | 推荐度 |
|----------|------|--------|--------|
| `@Inject` | 在方法内注入代码 | 高 | ⭐⭐⭐⭐⭐ |
| `@Redirect` | 重定向单个方法调用 | 中 | ⭐⭐⭐⭐ |
| `@ModifyArg` | 修改方法调用的单个参数 | 高 | ⭐⭐⭐⭐ |
| `@ModifyArgs` | 修改方法调用的多个参数 | 高 | ⭐⭐⭐ |
| `@ModifyReturnValue` | 修改方法返回值 | 高 | ⭐⭐⭐⭐ |
| `@ModifyVariable` | 修改局部变量值 | 中 | ⭐⭐⭐ |
| `@WrapOperation` | 包装条件性操作（1.21+） | 高 | ⭐⭐⭐⭐⭐ |
| `@Overwrite` | 完全替换方法 | 极低 | ⭐ |

## @Inject — 最通用的注入

在目标方法的指定位置插入代码。

```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    // 在 tick() 方法开头执行
}

@Inject(method = "tick", at = @At("RETURN"))
private void onTickReturn(CallbackInfo ci) {
    // 在 tick() 方法每个 return 前执行
}

// 取消原方法执行
@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
private void onCanAttack(CallbackInfoReturnable<Boolean> cir) {
    cir.setReturnValue(false);  // 替换返回值
}
```

### @At 注入点

| 值 | 含义 | 注意 |
|----|------|------|
| `HEAD` | 方法开头 | 最安全，最常用 |
| `RETURN` | 每个 return 前 | 用于修改返回值 |
| `TAIL` | 方法末尾（最后一个 return 前） | 注意死循环路径 |
| `INVOKE` | 调用指定方法时 | 需要指定 target |
| `FIELD` | 字段访问时 | 需要指定 target |
| `LOAD` / `STORE` | 局部变量读写 | 需要指定 ordinal |

### @At("INVOKE") 详细用法

```java
// 在调用 this.world.getEntities() 时注入
@Inject(
    method = "tick",
    at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
        shift = At.Shift.AFTER  // 在调用之后而非之前
    )
)
```

**注意**: INVOKE 的 target 必须是完整的_OWNER+方法名+描述符_，否则匹配失败。

## @Redirect — 重定向方法调用

替换目标方法中的某个方法调用。

```java
// 将 tick() 中的 world.getEntities(...) 替换为自定义逻辑
@Redirect(
    method = "tick",
    at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
    )
)
private List<Entity> redirectGetEntities(Level world, Entity entity, AABB box) {
    return Collections.emptyList();  // 替换为返回空列表
}
```

**兼容性风险**: @Redirect 只能有一个 Mixin 对同一个调用点做重定向。两个 mod 对同一调用做 @Redirect 会冲突。优先用 @ModifyArg 或 @WrapOperation。

## @ModifyArg — 修改单个参数

修改目标方法调用中某个方法的单个参数。

```java
// 修改 drawString() 的颜色参数
@ModifyArg(
    method = "render",
    at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I"
    ),
    index = 4  // 第5个参数（0-indexed），即颜色 int
)
private int modifyColor(int originalColor) {
    return 0xFF0000;  // 改为红色
}
```

**优点**: 兼容性好，多个 @ModifyArg 可以链式修改同一调用的不同参数。

## @ModifyReturnValue — 修改返回值

```java
@ModifyReturnValue(
    method = "getMaxHealth",
    at = @At("RETURN")
)
private float modifyMaxHealth(float original) {
    return original * 2.0f;
}
```

**注意**: 这是 Mixin 0.12+ 新增的，更安全地替代 @Inject + cir.setReturnValue() 的模式。

## @WrapOperation — 条件性包装（推荐用于复杂逻辑）

Mixin 0.12+ 新增，用于替代 @Redirect 的场景，兼容性更好。

```java
@WrapOperation(
    method = "tick",
    at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
    )
)
private List<Entity> wrapGetEntities(Operation<List<Entity>> original, Level world, Entity entity, AABB box) {
    if (shouldSkipEntities()) {
        return Collections.emptyList();
    }
    return original.call(world, entity, box);  // 调用原始方法
}
```

**优点**: 多个 @WrapOperation 可以对同一调用链式包装，不会像 @Redirect 那样冲突。

## @Accessor — 访问私有字段（接口 Mixin）

```java
@Mixin(TargetClass.class)
public interface TargetClassAccessor {
    @Accessor("privateField")
    Type getPrivateField();

    @Accessor("privateField")
    void setPrivateField(Type value);
}
```

不注入方法，只生成 getter/setter 访问私有字段。

## @Invoker — 调用私有方法（接口 Mixin）

```java
@Mixin(TargetClass.class)
public interface TargetClassInvoker {
    @Invoker("privateMethod")
    Type invokePrivateMethod(Type param);
}
```

生成可调用的包装方法访问私有方法。

## 选择决策树

```
你要做什么？
├─ 在方法中插入代码 → @Inject
├─ 修改方法调用的参数
│   ├─ 只改一个参数 → @ModifyArg
│   └─ 改多个参数 → @ModifyArgs
├─ 修改方法返回值 → @ModifyReturnValue
├─ 替换整个方法调用
│   ├─ 需要兼容其他 mod → @WrapOperation
│   └─ 确定不会冲突 → @Redirect（不推荐）
├─ 修改局部变量 → @ModifyVariable
├─ 访问私有字段 → @Accessor
├─ 调用私有方法 → @Invoker
└─ 完全替换方法 → @Overwrite（强烈不推荐）
```
