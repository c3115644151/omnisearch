package com.cy311.omnisearch.data.model.document;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DocNodeAdapterFactory implements TypeAdapterFactory {

    private static final Map<String, Class<? extends DocNode>> NAME_TO_CLASS = new HashMap<>();
    private static final Map<Class<? extends DocNode>, String> CLASS_TO_NAME = new HashMap<>();

    static {
        register("heading", HeadingNode.class);
        register("paragraph", ParagraphNode.class);
        register("table", TableNode.class);
        register("list", ListNode.class);
        register("image", ImageNode.class);
        register("link", LinkNode.class);
        register("divider", DividerNode.class);
        register("section", SectionNode.class);
        register("text", TextNode.class);
        register("styled_text", StyledTextNode.class);
        register("image_inline", ImageInlineNode.class);
    }

    private static void register(String name, Class<? extends DocNode> clazz) {
        NAME_TO_CLASS.put(name, clazz);
        CLASS_TO_NAME.put(clazz, name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != DocNode.class) {
            return null;
        }
        return (TypeAdapter<T>) new DocNodeTypeAdapter(gson);
    }

    private static class DocNodeTypeAdapter extends TypeAdapter<DocNode> {
        private final Gson gson;

        DocNodeTypeAdapter(Gson gson) {
            this.gson = gson;
        }

        @Override
        public void write(JsonWriter out, DocNode value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            JsonObject obj = gson.toJsonTree(value, value.getClass()).getAsJsonObject();
            String typeName = CLASS_TO_NAME.get(value.getClass());
            if (typeName != null) {
                obj.addProperty("type", typeName);
            }
            gson.toJson(obj, out);
        }

        @Override
        public DocNode read(JsonReader in) throws IOException {
            JsonObject obj = gson.fromJson(in, JsonObject.class);
            JsonElement typeEl = obj.get("type");
            if (typeEl == null) {
                throw new JsonParseException("Missing 'type' discriminator for DocNode");
            }
            String typeName = typeEl.getAsString();
            Class<? extends DocNode> clazz = NAME_TO_CLASS.get(typeName);
            if (clazz == null) {
                throw new JsonParseException("Unknown DocNode type: " + typeName);
            }
            obj.remove("type");
            return gson.fromJson(obj, clazz);
        }
    }
}
