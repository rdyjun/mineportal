package com.mineportal.mc;

import com.viaversion.viabackwards.ViaBackwardsPlatformImpl;
import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaCodecHandler;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.netty.DefaultPacketHandlerExecutor;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * MCProtocolLib은 한 가지 MC 버전(26.1)만 말할 수 있다. 서버가 다른 버전이면 netty 파이프라인의
 * 압축 해제와 패킷 해석 사이에 ViaVersion 번역 단계를 끼워서, 우리는 계속 26.1로 말하고 서버는
 * 자기 버전으로 받게 한다. 구성은 ViaProxy(Proxy2ServerChannelInitializer)와 같다.
 * ViaBackwards만 넣었으므로 ViaBackwards가 지원하는 범위(1.9 이상)까지 접속할 수 있다.
 */
final class ViaTranslation {

    private static final Logger LOGGER = Logger.getLogger("ViaTranslation");
    private static final AttributeKey<ProtocolVersion> TARGET_VERSION = AttributeKey.valueOf("mineportal:target-version");
    private static boolean loaded;

    private ViaTranslation() {
    }

    /**
     * serverProtocol로 말하는 서버에 접속할 세션. 우리와 같은 버전이거나 Via가 모르는 번호면
     * 번역 없이 평범한 세션을 돌려준다.
     */
    static ClientSession createSession(InetSocketAddress address, MinecraftProtocol protocol, int serverProtocol) {
        Executor executor = DefaultPacketHandlerExecutor.createExecutor();
        if (serverProtocol == protocol.getCodec().getProtocolVersion() || !ProtocolVersion.isRegistered(serverProtocol)) {
            return new ClientNetworkSession(address, protocol, executor, null, null);
        }
        init();
        ProtocolVersion target = ProtocolVersion.getProtocol(serverProtocol);
        return new ClientNetworkSession(address, protocol, executor, null, null) {
            @Override
            @SuppressWarnings("unchecked")
            protected ChannelHandler getChannelHandler() {
                return new ViaChannelInitializer((ChannelInitializer<Channel>) super.getChannelHandler(), true) {
                    @Override
                    protected void injectPipeline(ChannelPipeline pipeline, UserConnection connection) {
                        pipeline.channel().attr(TARGET_VERSION).set(target);
                        pipeline.addBefore("codec", ViaCodecHandler.NAME, new LenientViaCodec(connection));
                    }
                };
            }
        };
    }

    private static synchronized void init() {
        if (loaded) return;
        File dataFolder = new File(System.getProperty("java.io.tmpdir"), "mineportal-via");
        ViaManagerImpl.initAndLoad(new Platform(dataFolder), new Injector(), new ViaCommandHandler(false),
                new Loader(), ViaBackwardsPlatformImpl::new);
        loaded = true;
    }

    /**
     * 모드 서버는 Via가 모르는 블록/엔티티/아이템 데이터를 보낼 수 있다. 우리는 채팅/명령어만 쓰므로
     * 번역에 실패한 패킷은 버리고 연결을 유지한다(ViaProxy의 ignore-protocol-translation-errors와 같음).
     */
    private static final class LenientViaCodec extends ViaCodecHandler {
        LenientViaCodec(UserConnection connection) {
            super(connection);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                super.channelRead(ctx, msg);
            } catch (Throwable e) {
                LOGGER.fine("Dropped untranslatable packet: " + e);
            }
        }
    }

    /** Via가 파이프라인에서 자기 핸들러를 찾을 때 쓰는 이름 — 단일 코덱 핸들러를 쓰므로 둘 다 같다. */
    private static final class Injector extends NoopInjector {
        @Override
        public String getEncoderName() {
            return ViaCodecHandler.NAME;
        }

        @Override
        public String getDecoderName() {
            return ViaCodecHandler.NAME;
        }
    }

    private static final class Platform extends UserConnectionViaVersionPlatform {
        Platform(File dataFolder) {
            super(dataFolder);
        }

        @Override
        public Logger createLogger(String name) {
            return Logger.getLogger(name);
        }

        @Override
        public boolean isProxy() {
            return true;
        }

        @Override
        public String getPlatformName() {
            return "MinePortal";
        }

        @Override
        public String getPlatformVersion() {
            return "1";
        }
    }

    private static final class Loader implements ViaPlatformLoader {
        @Override
        public void load() {
            Via.getManager().getProviders().use(VersionProvider.class, new BaseVersionProvider() {
                @Override
                public ProtocolVersion getClosestServerProtocol(UserConnection connection) {
                    return connection.getChannel().attr(TARGET_VERSION).get();
                }
            });
        }

        @Override
        public void unload() {
        }
    }
}
