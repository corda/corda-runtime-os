package net.corda.v5.ledger.obsolete;

import net.corda.v5.membership.GroupParameters;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;

public class LedgerGroupParametersJavaApiTest {

    private final GroupParameters groupParameters = mock(GroupParameters.class);
    private final NotaryInfo notaryInfo = mock(NotaryInfo.class);
    private final List<NotaryInfo> notaryInfoList = List.of(notaryInfo);

    @Test
    public void getNotaryServiceParty() {
        String notariesKey = "corda.notaries";
        when(groupParameters.parseList(eq(notariesKey), eq(NotaryInfo.class))).thenReturn(notaryInfoList);

        var result = LedgerGroupParameters.getNotaries(groupParameters);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfoList);
    }
}
