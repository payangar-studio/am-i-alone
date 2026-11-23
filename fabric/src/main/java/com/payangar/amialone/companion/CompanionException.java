package com.payangar.amialone.companion;

import net.minecraft.network.chat.Component;

/** A companion request the server refuses, with the message to show whoever made it. */
public final class CompanionException extends Exception {
    private final transient Component reason;

    public CompanionException(Component reason) {
        super(reason.getString());
        this.reason = reason;
    }

    public Component reason() {
        return reason;
    }
}
