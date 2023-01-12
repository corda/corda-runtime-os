package net.corda.flow.pipeline.factory.sample.flows;

import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.RestStartableFlow;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.application.messaging.FlowSession;
import org.jetbrains.annotations.NotNull;

public class ExampleJavaFlow implements RestStartableFlow, ResponderFlow {
    @Override
    public void call(@NotNull FlowSession session) {
    }

    @NotNull
    @Override
    public String call(@NotNull RestRequestBody requestBody) {
        return ExampleJavaFlow.class.getSimpleName();
    }
}
