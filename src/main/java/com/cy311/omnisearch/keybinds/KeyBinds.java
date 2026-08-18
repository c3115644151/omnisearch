package com.cy311.omnisearch.keybinds;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

// verified: KeyMapping constructor (String, InputConstants.Type, int, String) from NeoForge docs https://docs.neoforged.net/docs/1.21.1/misc/keymappings 2026-06-14
// verified: conflict-context constructor exists in NeoForge 1.21.x KeyMapping Javadoc 2026-08-17
// verified: KeyConflictContext.GUI / IN_GAME exist in NeoForge 1.21.x Javadoc 2026-08-17
// verified: RegisterKeyMappingsEvent.register(KeyMapping) from NeoForge GitHub 1.21.1 branch 2026-06-14
public class KeyBinds {
    public static final String CATEGORY = "key.categories.omnisearch";

    public static final KeyMapping openSearch = new KeyMapping(
            "key.omnisearch.open",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY
    );

    public static final KeyMapping inspectHoveredItem = new KeyMapping(
            "key.omnisearch.inspect_hovered",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(openSearch);
        event.register(inspectHoveredItem);
    }
}
