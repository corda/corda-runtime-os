package net.corda.v5.application.messaging;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FlowInfoJavaApiTest {

    private final FlowInfo flowInfo = new FlowInfo(5, "test");

    @Test
    public void getFlowVersion() {
        int result = flowInfo.getFlowVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(5);
    }

    @Test
    public void getAppName() {
        String result = flowInfo.getAppName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("test");
    }
}
