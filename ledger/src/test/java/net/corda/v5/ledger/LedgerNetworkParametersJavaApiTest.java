package net.corda.v5.ledger;

import net.corda.v5.application.node.NetworkParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LedgerNetworkParametersJavaApiTest {

    private final NetworkParameters networkParameters = mock(NetworkParameters.class);
    private final NotaryInfo notaryInfo = mock(NotaryInfo.class);
    private final List<NotaryInfo> notaryInfoList = List.of(notaryInfo);

    @Test
    public void getNotaryServiceParty() {
        when(networkParameters.get(LedgerNetworkParameters.NOTARIES_KEY)).thenReturn(notaryInfoList);

        var result = LedgerNetworkParameters.getNotaries(networkParameters);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfoList);
    }

    @Test
    public void notaries_Key() {
        var result = LedgerNetworkParameters.NOTARIES_KEY;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("corda.notaries");
    }
}
