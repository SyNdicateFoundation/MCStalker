package ir.xenoncommunity.scan;

import ir.xenoncommunity.config.Config;
import ir.xenoncommunity.utils.PacketUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.*;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.InetSocketAddress;

@Getter
@Setter
@AllArgsConstructor
public class Connection implements Runnable {

    private String ip;
    private int port;
    private Config config;

    @Override
    public void run() {
        final IoConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(config.getTimeOut());
        connector.getFilterChain().addLast("logger", new LoggingFilter());
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new BinaryProtocolCodecFactory()));
        connector.setHandler(new StalkerHandler(ip, port));
        final ConnectFuture future = connector.connect(new InetSocketAddress(ip, port));
        future.awaitUninterruptibly(config.getTimeOut());
        if (!future.isConnected()) {
            System.err.println("Failed");
            future.getException().printStackTrace();
            connector.dispose();
            return;
        }
        future.getSession().getCloseFuture().awaitUninterruptibly(config.getTimeOut());
        connector.dispose();
    }

    @AllArgsConstructor
    public static class StalkerHandler extends IoHandlerAdapter {
        private String ip;
        private int port;

        @Override
        public void sessionOpened(IoSession session) throws Exception {
            session.write(PacketUtils.createStatusPacket(ip, port, 47));
        }

        @Override
        public void messageReceived(IoSession session, Object message) {
            System.out.println((String)message);
            session.closeNow();
        }

        @Override
        public void sessionClosed(IoSession session) {
            System.out.println("Closed");
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) {
            cause.printStackTrace();
            session.closeNow();
        }
    }

    public static class BinaryProtocolCodecFactory implements ProtocolCodecFactory {
        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            return new BinaryProtocolEncoder();
        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) {
            return new BinaryProtocolDecoder();
        }
    }

    public static class BinaryProtocolEncoder implements ProtocolEncoder {
        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
            if (message instanceof byte[]) {
                final byte[] packetBytes = (byte[]) message;
                final IoBuffer buffer = IoBuffer.allocate(packetBytes.length).setAutoExpand(true);
                buffer.put(packetBytes);
                buffer.flip();
                out.write(buffer);
            } else {
                throw new IllegalArgumentException("Message must be a byte array");
            }
        }

        @Override
        public void dispose(IoSession session) {}
    }

    public static class BinaryProtocolDecoder implements ProtocolDecoder {
        @Override
        public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
            if (in.remaining() < 3) {
                return;
            }
            in.mark();

            final byte[] bytes = new byte[in.remaining()];
            in.get(bytes);

            try (final DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
                out.write(PacketUtils.readString(dis).substring(3));
            } catch (Exception e) {
                in.reset();
                throw e;
            }
        }

        @Override
        public void finishDecode(IoSession session, ProtocolDecoderOutput out) {}

        @Override
        public void dispose(IoSession session) {}
    }
}