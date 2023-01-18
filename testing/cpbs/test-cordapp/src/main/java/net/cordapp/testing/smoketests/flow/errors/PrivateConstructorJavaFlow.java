package net.cordapp.testing.smoketests.flow.errors;

import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class PrivateConstructorJavaFlow implements ClientStartableFlow {
    private PrivateConstructorJavaFlow() {
    }

    @NotNull
    @Override
    public String call(@NotNull RestRequestBody requestBody) {
        throw new IllegalStateException("Should not reach this point");
    }
}
