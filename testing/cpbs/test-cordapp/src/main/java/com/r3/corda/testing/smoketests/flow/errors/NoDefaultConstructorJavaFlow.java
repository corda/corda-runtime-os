package com.r3.corda.testing.smoketests.flow.errors;

import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class NoDefaultConstructorJavaFlow implements ClientStartableFlow {
    private final String message;

    public NoDefaultConstructorJavaFlow(String message) {
        this.message = message;
    }

    @NotNull
    @Override
    public String call(@NotNull ClientRequestBody requestBody) {
        throw new IllegalStateException(message);
    }
}
