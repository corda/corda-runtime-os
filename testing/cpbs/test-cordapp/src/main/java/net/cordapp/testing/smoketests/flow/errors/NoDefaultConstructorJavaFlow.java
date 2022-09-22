package net.cordapp.testing.smoketests.flow.errors;

import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.flows.RPCStartableFlow;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class NoDefaultConstructorJavaFlow implements RPCStartableFlow {
    private final String message;

    public NoDefaultConstructorJavaFlow(String message) {
        this.message = message;
    }

    @NotNull
    @Override
    public String call(@NotNull RPCRequestData requestBody) {
        throw new IllegalStateException(message);
    }
}
