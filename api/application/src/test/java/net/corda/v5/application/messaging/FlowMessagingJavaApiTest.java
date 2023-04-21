package net.corda.v5.application.messaging;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowMessagingJavaApiTest {

    private final FlowMessaging flowMessaging = mock(FlowMessaging.class);
    private final FlowSession flowSession = mock(FlowSession.class);

    @Test
    public void initiateFlowParty() {
        final MemberX500Name counterparty = new MemberX500Name("Alice Corp", "LDN", "GB");
        when(flowMessaging.initiateFlow(counterparty)).thenReturn(flowSession);

        FlowSession result = flowMessaging.initiateFlow(counterparty);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(flowSession);
    }

    @Test
    public void initiateFlowPartyWithBuilder() {
        final MemberX500Name counterparty = new MemberX500Name("Alice Corp", "LDN", "GB");
        when(flowMessaging.initiateFlow(eq(counterparty), any())).thenReturn(flowSession);

        FlowSession result = flowMessaging.initiateFlow(counterparty, (contextProperties) -> contextProperties.put("key", "value"));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(flowSession);
    }
}
