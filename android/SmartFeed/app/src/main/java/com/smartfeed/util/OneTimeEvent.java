package com.smartfeed.util;

public final class OneTimeEvent<T> {

    private final T content;
    private boolean handled;

    public OneTimeEvent(T content) {
        this.content = content;
    }

    public synchronized T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}
