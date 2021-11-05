package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class FlowExternalOperationJavaApiTest {

    private final MyFlowExternalOperation flowExternalOperation = new MyFlowExternalOperation("testProperty");

    @Test
    public void execute() {
        String result = flowExternalOperation.execute("testProperty");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testProperty");
    }

    static class MyFlowExternalOperation implements FlowExternalOperation<String> {

        private final String property;

        MyFlowExternalOperation(String property) {
            this.property = property;
        }

        @Override
        public String execute(@NotNull String deduplicationId) {
            return property;
        }
    }
}

