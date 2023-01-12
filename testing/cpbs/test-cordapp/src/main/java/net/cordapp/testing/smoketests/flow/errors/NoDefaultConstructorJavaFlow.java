package net.cordapp.testing.smoketests.flow.errors;

import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.RestStartableFlow;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class NoDefaultConstructorJavaFlow implements RestStartableFlow {
    private final String message;

    public NoDefaultConstructorJavaFlow(String message) {
        this.message = message;
    }

    @NotNull
    @Override
    public String call(@NotNull RestRequestBody requestBody) {
        throw new IllegalStateException(message);
    }
}
