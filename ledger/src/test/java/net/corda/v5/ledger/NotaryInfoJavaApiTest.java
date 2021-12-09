package net.corda.v5.ledger;

import net.corda.v5.application.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotaryInfoJavaApiTest {

    private final NotaryInfo notaryInfo = mock(NotaryInfo.class);

    @Test
    public void getPluginClass() {
        String test = "test";
        when(notaryInfo.getPluginClass()).thenReturn(test);
        String result = notaryInfo.getPluginClass();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void getParty() {
        Party party = mock(Party.class);
        when(notaryInfo.getParty()).thenReturn(party);
        Party result = notaryInfo.getParty();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }
}
