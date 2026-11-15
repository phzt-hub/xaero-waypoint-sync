package com.phzt.waypointsync;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 三条自定义包：上传分片（C2S）、拉取请求（C2S）、数据分片（S2C）。 */
public final class SyncPayloads {
    /** writeUtf 的硬上限。分片逻辑保证实际内容远小于它。 */
    private static final int MAX_UTF = 32767;

    private SyncPayloads() {
    }

    public record UploadPayload(int session, int part, int total, String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UploadPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WaypointSync.MOD_ID, "upload"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UploadPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.session());
                    buf.writeVarInt(payload.part());
                    buf.writeVarInt(payload.total());
                    buf.writeUtf(payload.json(), MAX_UTF);
                },
                buf -> new UploadPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_UTF)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WaypointSync.MOD_ID, "request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                },
                buf -> new RequestPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DataPayload(int part, int total, String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DataPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WaypointSync.MOD_ID, "data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.part());
                    buf.writeVarInt(payload.total());
                    buf.writeUtf(payload.json(), MAX_UTF);
                },
                buf -> new DataPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_UTF)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(UploadPayload.TYPE, UploadPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DataPayload.TYPE, DataPayload.CODEC);
    }
}
