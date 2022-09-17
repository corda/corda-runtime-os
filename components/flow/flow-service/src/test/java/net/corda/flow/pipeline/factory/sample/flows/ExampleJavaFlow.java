package net.corda.flow.pipeline.factory.sample.flows;

import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.flows.RPCStartableFlow;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.application.messaging.FlowSession;
import org.jetbrains.annotations.NotNull;

public class ExampleJavaFlow implements RPCStartableFlow, ResponderFlow {
    @Override
    public void call(@NotNull FlowSession session) {
    }

    @NotNull
    @Override
    public String call(@NotNull RPCRequestData requestBody) {
        return ExampleJavaFlow.class.getSimpleName();
    }
}
