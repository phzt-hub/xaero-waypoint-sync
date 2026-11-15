package com.phzt.waypointsync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/** 线上格式的公共部分：JSON 编解码 + 分片。上传和下发用的是同一套记录格式。 */
public final class Wire {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** 单片 JSON 的预算（字符数）。留足余量：FriendlyByteBuf.writeUtf 的上限是 32767 字节。 */
    private static final int CHUNK_BUDGET = 18_000;

    private Wire() {
    }

    /**
     * 把记录切成若干段 JSON 数组，每段不超过 {@link #CHUNK_BUDGET} 字符。
     *
     * <p>按序列化后的实际长度切，而不是按条数：waypoint 名字里可以塞中文，一条记录可能上百字节。
     */
    public static List<String> chunk(List<WaypointRecord> records) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder("[");
        boolean first = true;
        for (WaypointRecord record : records) {
            String json = GSON.toJson(record);
            if (!first && current.length() + json.length() + 1 > CHUNK_BUDGET) {
                current.append(']');
                chunks.add(current.toString());
                current = new StringBuilder("[");
                first = true;
            }
            if (!first) current.append(',');
            current.append(json);
            first = false;
        }
        if (!first) {
            current.append(']');
            chunks.add(current.toString());
        }
        return chunks;
    }

    public static List<WaypointRecord> parseAll(List<String> chunks) {
        List<WaypointRecord> out = new ArrayList<>();
        for (String chunk : chunks) {
            WaypointRecord[] parsed = GSON.fromJson(chunk, WaypointRecord[].class);
            if (parsed == null) continue;
            for (WaypointRecord record : parsed) {
                if (record != null) out.add(record);
            }
        }
        return out;
    }
}
