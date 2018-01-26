package com.borunovv.wsserver.protocol;

import com.borunovv.wsserver.nio.RWSession;

public abstract class AbstractMessage {
    private RWSession session;

    public AbstractMessage(RWSession session) {
        this.session = session;
    }

    public RWSession getSession() {
        return session;
    }
}