package com.mineportal.mc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.codec.PacketDefinition;
import org.geysermc.mcprotocollib.network.packet.PacketRegistry;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacketSerializer;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;

/**
 * Forge 서버에 "Forge가 설치된 클라이언트"인 척 접속하기 위한 최소 핸드셰이크.
 * 우리는 채팅/명령어만 쓰므로 모드 내용은 필요 없다 — 서버가 묻는 것에 통과할 만큼만 답한다.
 * <ul>
 *   <li>1.20.2 이상: configuration 단계의 forge:handshake 채널. 출처: MinecraftForge 26.1
 *       net.minecraftforge.network(NetworkContext, ForgePacketHandler). 페이로드는
 *       [VarInt 판별자][본문].</li>
 *   <li>1.13~1.20.1(FML2/FML3): login 단계의 fml:loginwrapper 커스텀 쿼리. 출처: MinecraftForge
 *       1.20.1 net.minecraftforge.network(LoginWrapper, HandshakeMessages, HandshakeHandler).
 *       쿼리 데이터는 [내부 채널 이름][VarInt 길이][Byte 판별자][본문].</li>
 * </ul>
 */
final class ForgeHandshake {

    /** 1.20.2 — Forge가 핸드셰이크를 login 단계에서 configuration 단계로 옮긴 버전. */
    private static final int PROTOCOL_1_20_2 = 764;

    private static final Key CHANNEL = Key.key("forge", "handshake");
    private static final int ACKNOWLEDGE = 0;
    private static final int MOD_VERSIONS = 1;
    private static final int CHANNEL_VERSIONS = 2;
    private static final int REGISTRY_LIST = 3;
    private static final int REGISTRY_DATA = 4;

    private static final Key FML_LOGIN_WRAPPER = Key.key("fml", "loginwrapper");
    private static final Key FML_HANDSHAKE = Key.key("fml", "handshake");
    private static final int FML_MOD_LIST = 1;
    private static final int FML_MOD_LIST_REPLY = 2;
    private static final int FML_REGISTRY = 3;
    private static final int FML_CONFIG_DATA = 4;
    private static final int FML_ACKNOWLEDGE = 99;

    private ForgeHandshake() {
    }

    /**
     * Forge와 모드는 명령어 인자 타입(forge:enum 등)을 추가하는데, MCProtocolLib은 이걸 몰라서
     * ClientboundCommandsPacket 해석에 실패하고 연결을 끊는다. 공용 코덱의 해석기를 바꿔서
     * 실패하면 빈 명령어 트리로 대신한다 — 그러면 명령어는 서명 없이 보내진다.
     * 알 수 없는 인자 타입은 속성 길이도 알 수 없어서 트리의 일부만 살릴 수는 없다.
     * 공용 static 코덱을 바꾸므로 첫 접속 전에 한 번만 부른다.
     */
    static void install() {
        PacketRegistry game = MinecraftCodec.CODEC.getCodec(ProtocolState.GAME);
        int id = game.getClientboundId(ClientboundCommandsPacket.class);
        game.registerClientbound(new PacketDefinition<>(id, ClientboundCommandsPacket.class,
                new MinecraftPacketSerializer<>(buf -> {
                    try {
                        return new ClientboundCommandsPacket(buf);
                    } catch (RuntimeException e) {
                        buf.readerIndex(buf.writerIndex()); // 남은 바이트 검사에 걸리지 않도록 버린다
                        return new ClientboundCommandsPacket(new CommandNode[0], 0);
                    }
                })));
    }

    /**
     * ClientIntention의 host 뒤에 붙는 Forge 표식. fmlNetworkVersion은 서버 ping의
     * forgeData.fmlNetworkVersion(1.20.1 이하는 2/3), serverProtocol은 서버의 프로토콜 번호.
     */
    static String markHost(String host, int fmlNetworkVersion, int serverProtocol) {
        if (serverProtocol >= PROTOCOL_1_20_2) return host + "\0FORGE"; // NetworkContext.enhanceHostName
        return host + "\0FML" + fmlNetworkVersion + "\0";             // NetworkConstants.NETVERSION
    }

    /** 1.20.2+ forge:handshake 요청에 대한 응답 패킷, 답할 필요 없으면 null. */
    static ServerboundCustomPayloadPacket reply(ClientboundCustomPayloadPacket packet) {
        if (!CHANNEL.equals(packet.getChannel())) return null;
        byte[] data = packet.getData();
        ByteBuf in = Unpooled.wrappedBuffer(data);
        int id = MinecraftTypes.readVarInt(in);
        return switch (id) {
            // 모드/채널 목록은 서버가 보낸 것을 그대로 돌려준다 — 서버는 클라이언트 채널
            // 목록이 자기 것과 맞는지만 검사하므로 똑같이 돌려주면 항상 통과한다.
            case MOD_VERSIONS, CHANNEL_VERSIONS -> new ServerboundCustomPayloadPacket(CHANNEL, data);
            // 레지스트리 동기화는 토큰에 대한 Acknowledge만 보내면 다음으로 넘어간다.
            case REGISTRY_LIST, REGISTRY_DATA -> acknowledge(MinecraftTypes.readVarInt(in));
            default -> null; // ConfigData 등은 응답이 필요 없다
        };
    }

    private static ServerboundCustomPayloadPacket acknowledge(int token) {
        ByteBuf out = Unpooled.buffer();
        MinecraftTypes.writeVarInt(out, ACKNOWLEDGE);
        MinecraftTypes.writeVarInt(out, token);
        return new ServerboundCustomPayloadPacket(CHANNEL, bytes(out));
    }

    /**
     * 1.20.1 이하 login 단계 커스텀 쿼리에 대한 응답, 답하지 말아야 하면 null. Forge가 아닌
     * 쿼리에는 바닐라 클라이언트처럼 "모름"(데이터 없음)으로 답한다.
     */
    static ServerboundCustomQueryAnswerPacket reply(ClientboundCustomQueryPacket packet) {
        if (!FML_LOGIN_WRAPPER.equals(packet.getChannel())) {
            return new ServerboundCustomQueryAnswerPacket(packet.getMessageId());
        }
        ByteBuf in = Unpooled.wrappedBuffer(packet.getData());
        Key target = MinecraftTypes.readResourceLocation(in);
        MinecraftTypes.readVarInt(in); // 감싼 페이로드 길이
        if (!FML_HANDSHAKE.equals(target)) return null;

        ByteBuf out = Unpooled.buffer();
        switch (in.readUnsignedByte()) {
            case FML_MOD_LIST -> {
                // S2CModList의 앞부분(모드 목록, 채널 목록)은 C2SModListReply와 형식이 같다 —
                // 그대로 돌려주면 서버의 채널 검사를 통과한다. 레지스트리 해시는 Forge
                // 클라이언트처럼 빈 맵으로 보낸다.
                int start = in.readerIndex();
                int mods = MinecraftTypes.readVarInt(in);
                for (int i = 0; i < mods; i++) MinecraftTypes.readString(in);
                int channels = MinecraftTypes.readVarInt(in);
                for (int i = 0; i < channels * 2; i++) MinecraftTypes.readString(in);
                out.writeByte(FML_MOD_LIST_REPLY);
                out.writeBytes(in, start, in.readerIndex() - start);
                MinecraftTypes.writeVarInt(out, 0);
            }
            case FML_REGISTRY, FML_CONFIG_DATA -> out.writeByte(FML_ACKNOWLEDGE);
            default -> {
                return null; // S2CModData 등은 Forge 클라이언트도 답하지 않는다
            }
        }
        ByteBuf wrapped = Unpooled.buffer();
        MinecraftTypes.writeResourceLocation(wrapped, FML_HANDSHAKE);
        MinecraftTypes.writeVarInt(wrapped, out.readableBytes());
        wrapped.writeBytes(out);
        return new ServerboundCustomQueryAnswerPacket(packet.getMessageId(), bytes(wrapped));
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
