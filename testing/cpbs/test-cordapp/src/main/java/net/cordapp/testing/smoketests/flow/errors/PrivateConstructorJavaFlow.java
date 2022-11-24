package net.cordapp.testing.smoketests.flow.errors;

import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.flows.RPCStartableFlow;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class PrivateConstructorJavaFlow implements RPCStartableFlow {
    private PrivateConstructorJavaFlow() {
    }

    @NotNull
    @Override
    public String call(@NotNull RPCRequestData requestBody) {
        throw new IllegalStateException("Should not reach this point");
    }
}
