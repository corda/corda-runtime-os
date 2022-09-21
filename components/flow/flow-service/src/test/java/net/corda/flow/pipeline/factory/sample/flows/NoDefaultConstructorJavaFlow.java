package net.corda.flow.pipeline.factory.sample.flows;

import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.flows.RPCStartableFlow;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.application.messaging.FlowSession;
import org.jetbrains.annotations.NotNull;

public class NoDefaultConstructorJavaFlow implements RPCStartableFlow, ResponderFlow {
    private final String message;

    public NoDefaultConstructorJavaFlow(String message) {
        this.message = message;
    }

    @Override
    public void call(@NotNull FlowSession session) {
        throw new IllegalStateException(message);
    }

    @NotNull
    @Override
    public String call(@NotNull RPCRequestData requestBody) {
        throw new IllegalStateException(message);
    }
}
