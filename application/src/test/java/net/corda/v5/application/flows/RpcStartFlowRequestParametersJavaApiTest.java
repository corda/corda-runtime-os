package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class RpcStartFlowRequestParametersJavaApiTest {

    @Test
    public void parametersInJson() {
        final String json = "json";
        final RpcStartFlowRequestParameters rpcStartFlowRequestParameters = new RpcStartFlowRequestParameters(json);

        final String jsonTest = rpcStartFlowRequestParameters.getParametersInJson();

        Assertions.assertThat(jsonTest).isNotNull();
        Assertions.assertThat(jsonTest).isEqualTo(json);
    }
}
