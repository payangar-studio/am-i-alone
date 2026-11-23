package com.payangar.amialone.companion;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;

/**
 * The connection of a companion: nobody is at the other end. Built like the mock player of
 * vanilla's gametests, on an in-memory channel, but it drops what the server sends: a companion
 * lives as long as the world, and the channel would keep every packet.
 *
 * <p>The server never ticks it, since it is not in the server's connection list: the companion
 * ticks itself (see {@link CompanionPlayer#tick()}).
 */
final class CompanionConnection extends Connection {
    private CompanionConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    static CompanionConnection create() {
        CompanionConnection connection = new CompanionConnection();
        new EmbeddedChannel(connection);
        return connection;
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}
}
