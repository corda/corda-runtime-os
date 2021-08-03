package net.corda.v5.application.flows.flowservices;

import net.corda.v5.application.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowIdentityJavaApiTest {

    private final FlowIdentity flowIdentity = mock(FlowIdentity.class);

    @Test
    public void getOurIdentity() {
        Party test = mock(Party.class);
        when(flowIdentity.getOurIdentity()).thenReturn(test);

        Party result = flowIdentity.getOurIdentity();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
