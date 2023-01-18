package net.corda.flow.pipeline.factory.sample.flows;

import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.application.messaging.FlowSession;
import org.jetbrains.annotations.NotNull;

public class NoDefaultConstructorJavaFlow implements ClientStartableFlow, ResponderFlow {
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
    public String call(@NotNull RestRequestBody requestBody) {
        throw new IllegalStateException(message);
    }
}
