package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class FlowIdJavaApiTest {

    private final FlowId flowId = new FlowId(UUID.randomUUID());

    @Test
    public void createRandom() {
        FlowId result = FlowId.createRandom();

        Assertions.assertThat(result).isNotNull();
    }

    @Test
    public void toStringTest() {
        String result = flowId.toString();

        Assertions.assertThat(result).isNotNull();
    }
}
