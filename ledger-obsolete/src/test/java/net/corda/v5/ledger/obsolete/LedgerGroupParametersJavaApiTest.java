package net.corda.v5.ledger.obsolete;

import net.corda.v5.membership.GroupParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LedgerGroupParametersJavaApiTest {

    private final GroupParameters groupParameters = mock(GroupParameters.class);
    private final NotaryInfo notaryInfo = mock(NotaryInfo.class);
    private final List<NotaryInfo> notaryInfoList = List.of(notaryInfo);

    @Test
    public void getNotaryServiceParty() {
        when(groupParameters.get(LedgerGroupParameters.NOTARIES_KEY)).thenReturn(notaryInfoList);

        var result = LedgerGroupParameters.getNotaries(groupParameters);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfoList);
    }

    @Test
    public void notaries_Key() {
        var result = LedgerGroupParameters.NOTARIES_KEY;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("corda.notaries");
    }
}
