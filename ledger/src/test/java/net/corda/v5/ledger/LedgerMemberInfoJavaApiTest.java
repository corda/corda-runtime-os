package net.corda.v5.ledger;

import net.corda.v5.application.identity.Party;
import net.corda.v5.application.node.MemberContext;
import net.corda.v5.application.node.MemberInfo;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LedgerMemberInfoJavaApiTest {

    private final MemberInfo memberInfo = mock(MemberInfo.class);
    private final Party party = mock(Party.class);
    private final MemberContext memberContext = mock(MemberContext.class);

    @Test
    public void getNotaryServiceParty() {
        when(memberContext.get(LedgerMemberInfo.NOTARY_SERVICE_PARTY)).thenReturn(party);
        when(memberInfo.getMemberProvidedContext()).thenReturn(memberContext);
        var result = LedgerMemberInfo.getNotaryServiceParty(memberInfo);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void notary_Service_Party() {
        var result = LedgerMemberInfo.NOTARY_SERVICE_PARTY;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("corda.notaryServiceParty");
    }
}
