package com.cy311.omnisearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static KeyMapping create(String name, KeyConflictContext ctx, InputConstants.Type type, int key, String category) {
        try {
            Class<?> categoryClass = null;
            for (Class<?> c : KeyMapping.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Category")) { categoryClass = c; break; }
            }
            if (categoryClass != null) {
                Object misc = null;
                for (Field f : categoryClass.getDeclaredFields()) {
                    if (f.getName().equalsIgnoreCase("MISC")) { misc = f.get(null); break; }
                }
                if (misc == null) {
                    Object[] constants = categoryClass.getEnumConstants();
                    misc = constants != null && constants.length > 0 ? constants[0] : null;
                }
                Constructor<KeyMapping> ctor = KeyMapping.class.getConstructor(String.class, net.neoforged.neoforge.client.settings.IKeyConflictContext.class, InputConstants.Type.class, int.class, categoryClass);
                return ctor.newInstance(name, ctx, type, key, misc);
            }
        } catch (Throwable ignored) {
        }
        try {
            Constructor<KeyMapping> oldCtor = KeyMapping.class.getConstructor(String.class, net.neoforged.neoforge.client.settings.IKeyConflictContext.class, InputConstants.Type.class, int.class, String.class);
            return oldCtor.newInstance(name, ctx, type, key, category);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static final KeyMapping openOmnisearch = create(
            "omnisearch.open",
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "omnisearch"
    );
}
