package net.corda.flow.pipeline.factory.sample.flows;

import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.application.messaging.FlowSession;
import org.jetbrains.annotations.NotNull;

public class PrivateConstructorJavaFlow implements ClientStartableFlow, ResponderFlow {
    private PrivateConstructorJavaFlow() {
    }

    @Override
    public void call(@NotNull FlowSession session) {
        throw new IllegalStateException("Should not reach this point");
    }

    @NotNull
    @Override
    public String call(@NotNull RestRequestBody requestBody) {
        throw new IllegalStateException("Should not reach this point");
    }
}
