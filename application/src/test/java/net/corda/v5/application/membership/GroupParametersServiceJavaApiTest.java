package net.corda.v5.application.membership;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.membership.GroupParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GroupParametersServiceJavaApiTest {

    private final GroupParametersService groupParametersService = mock(GroupParametersService.class);
    private final SecureHash secureHash = new SecureHash("algorithm", "bytes".getBytes());

    @Test
    public void getCurrentHash() {
        when(groupParametersService.getCurrentHash()).thenReturn(secureHash);

        SecureHash test = groupParametersService.getCurrentHash();

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(secureHash);
    }

    @Test
    public void lookup() {
        GroupParameters networkParameters = mock(GroupParameters.class);
        when(groupParametersService.lookup(secureHash)).thenReturn(networkParameters);

        GroupParameters test = groupParametersService.lookup(secureHash);

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(networkParameters);
    }
}
