package com.portfolio.analyzer.integration;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Last prompt handed to the stub {@code MultimodalAIClient}. Shared by every IT
 * because they share one Spring context and one stub instance.
 */
public final class CapturedChatPrompt {

    private static final AtomicReference<String> LAST = new AtomicReference<>();

    private CapturedChatPrompt() {
    }

    public static void record(String prompt) {
        LAST.set(prompt);
    }

    public static String last() {
        return LAST.get();
    }

    public static void clear() {
        LAST.set(null);
    }
}
