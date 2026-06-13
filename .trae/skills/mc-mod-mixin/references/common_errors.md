# Mixin 常见错误与排查

## 编译期错误

### 1. @Shadow 类型不匹配
```
错误: @Shadow field type mismatch
原因: @Shadow 声明的字段类型与目标类中实际类型不同
修复: 在源码中确认字段的确切类型
```

### 2. 方法描述符语法错误
```
错误: Invalid target descriptor
原因: 描述符格式不正确（少了分号、包名用点号而非斜杠等）
修复: 使用 JVM 内部格式，包路径用 / 分隔，类名后加 ;
```

### 3. Mixin 类不是 abstract
```
错误: Mixin class must be abstract
原因: @Mixin 注解的类必须声明为 abstract
修复: 添加 abstract 修饰符
```

### 4. @Inject 方法签名错误
```
错误: Handler method signature mismatch
原因: @Inject 方法的参数与目标方法不匹配
修复:
  - void 方法: (CallbackInfo ci)
  - 有返回值方法: (CallbackInfoReturnable<ReturnType> cir)
  - 需要捕获局部变量: 额外添加 @Local 参数
```

## 运行期错误

### 5. Mixin 未加载
```
症状: Mixin 代码不执行，但无错误
排查:
  1. 检查 mixin.json 中是否列出了该 Mixin 类
  2. 检查 Mixin 类是否放在正确的数组（mixins/client/server）
  3. 检查 fabric.mod.json / neoforge.mods.toml 是否引用了 mixin.json
  4. 搜索日志中的 "mixin" 关键词，看是否有加载信息
```

### 6. 目标方法未找到
```
错误: @Inject/@Redirect could not locate target method
原因: method 参数指定的方法名在目标类中不存在
排查:
  1. 在源码中确认方法名拼写
  2. 检查是否用了错误映射名（Yarn vs 官方）
  3. 方法可能有重载，需要指定完整描述符
```

### 7. 目标 INVOKE 点未找到
```
错误: Could not find INVOKE target
原因: @At(value="INVOKE", target=...) 中的调用点不存在
排查:
  1. 确认目标方法确实在指定位置被调用
  2. 方法描述符必须精确匹配（包括返回类型）
  3. 版本差异：方法调用可能在不同版本中被重构
  4. 使用 by = 2 等参数调整搜索策略
```

### 8. Mixin 冲突
```
错误: Mixin conflict / Multiple @Redirect for same target
原因: 两个 mod 对同一目标做了不兼容的 Mixin
修复:
  - @Redirect → 改用 @WrapOperation（兼容链式包装）
  - @Overwrite → 改用 @Inject（不替换原方法）
  - @Inject → 通常不会冲突
```

### 9. Side 隔离违反
```
错误: ClassNotFoundException at runtime (dedicated server)
原因: 客户端专用 Mixin 引用了 net.minecraft.client 包的类
修复:
  - 确保客户端 Mixin 放在 mixin.json 的 "client" 数组中
  - Mixin 类本身放在 client 子包中
  - 不要在通用 Mixin 中引用客户端类
```

## 逻辑错误（不报错但行为不对）

### 10. @Inject 在错误的位置注入
```
症状: 代码执行了但时机不对
原因: @At 注入点选择不当
排查:
  - HEAD: 方法最开头
  - RETURN: 每个 return 前（可能有多个 return）
  - TAIL: 最后一个 return 前
  - INVOKE + shift: 方法调用前后
  - 确认目标方法中实际有哪些 return 和调用点
```

### 11. @Redirect 与原方法行为不一致
```
症状: 重定向后的方法参数或返回值不对
原因: @Redirect 方法的参数顺序必须与原方法调用匹配
排查: 仔细比对 @Redirect 方法签名与原始调用签名
  - 第一个参数是被调用对象（如果是实例方法）
  - 其余参数是原方法的参数
```

### 12. cir.setReturnValue() 后代码仍执行
```
症状: 设置了返回值但原方法后续逻辑仍然运行
原因: @Inject(cancellable = true) + cir.setReturnValue() 只阻止后续注入的执行
修复:
  - 如果需要完全阻止原方法执行，必须注入 HEAD + cancellable
  - setReturnValue() 后应该立即 return
  - 或使用 @ModifyReturnValue 更清晰地修改返回值
```

## 调试技巧

### 1. 查看已加载的 Mixin
```bash
# 启动参数添加
-Dmixin.debug.verbose=true
-Dmixin.debug.export=true
```
导出的类在 `.mixin.out/` 目录下，可以反编译查看 Mixin 应用结果。

### 2. 运行时日志
在 @Inject 方法中加入日志：
```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    System.out.println("[YourMod] Mixin tick injected!");  // 快速验证
}
```

### 3. IDE 断点
在 Mixin 方法中设置断点，调试时可以正常命中。

### 4. 检查 refmap
如果方法名在运行时与源码中不同，可能是 refmap 未正确生成：
```bash
# 检查 refmap 文件内容
cat build/resources/main/yourmod.refmap.json | jq .
```
