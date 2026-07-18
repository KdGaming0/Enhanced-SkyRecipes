package com.github.kdgaming0.skyrecipes.core.data.codec;

import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared MessagePack helpers for the binary cache codecs.
 */
final class CodecUtil {

    private CodecUtil() {
    }

    static void packStringCollection(MessagePacker packer, Collection<String> values) throws IOException {
        packer.packArrayHeader(values.size());
        for (String value : values) {
            packer.packString(value);
        }
    }

    static List<String> unpackStringList(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackArrayHeader();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(unpacker.unpackString());
        }
        return list;
    }

    static void packStringStringMap(MessagePacker packer, Map<String, String> map) throws IOException {
        packer.packMapHeader(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            packer.packString(e.getKey());
            packer.packString(e.getValue());
        }
    }

    static Map<String, String> unpackStringStringMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, String> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(unpacker.unpackString(), unpacker.unpackString());
        }
        return map;
    }

    static Map<String, Number> unpackStringNumberMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, Number> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            Value v = unpacker.unpackValue();
            if (v.isIntegerValue()) {
                map.put(key, v.asIntegerValue().asInt());
            } else if (v.isFloatValue()) {
                map.put(key, v.asFloatValue().toDouble());
            } else if (v.isNumberValue()) {
                map.put(key, v.asNumberValue().toDouble());
            }
        }
        return map;
    }

    static Map<String, Map<String, Number>> unpackStringNumberMapMap(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, Map<String, Number>> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            map.put(key, unpackStringNumberMap(unpacker));
        }
        return map;
    }
}
