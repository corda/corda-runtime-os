package net.corda.v5.ledger.services;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.ledger.NotaryInfo;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotaryLookupServiceJavaApiTest {

    private final NotaryLookupService notaryLookupService = mock(NotaryLookupService.class);
    private final MemberX500Name testMemberX500Name = new MemberX500Name("Bob Plc", "Rome", "IT");
    private final Party party = mock(Party.class);
    private final NotaryInfo notaryInfo = mock(NotaryInfo.class);
    private final List<NotaryInfo> notaryInfoList = List.of(notaryInfo);

    @Test
    public void getNotaryIdentities() {
        when(notaryLookupService.getNotaryServices()).thenReturn(notaryInfoList);

        List<NotaryInfo> result = notaryLookupService.getNotaryServices();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfoList);
    }

    @Test
    public void getNotaryByName() {
        when(notaryLookupService.lookup(testMemberX500Name)).thenReturn(notaryInfo);

        NotaryInfo result = notaryLookupService.lookup(testMemberX500Name);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfo);
    }

    @Test
    public void getNotaryByKey() {
        PublicKey testPublicKey = mock(PublicKey.class);
        when(notaryLookupService.lookup(testPublicKey)).thenReturn(notaryInfo);

        NotaryInfo result = notaryLookupService.lookup(testPublicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(notaryInfo);
    }

    @Test
    public void isNotary() {
        when(notaryLookupService.isNotary(party)).thenReturn(true);

        Boolean result = notaryLookupService.isNotary(party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
    }
}
