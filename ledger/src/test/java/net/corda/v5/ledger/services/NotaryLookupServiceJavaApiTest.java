package net.corda.v5.ledger.services;

import net.corda.v5.application.identity.CordaX500Name;
import net.corda.v5.application.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotaryLookupServiceJavaApiTest {

    private final NotaryLookupService notaryLookupService = mock(NotaryLookupService.class);
    private final Party party = mock(Party.class);
    private final List<Party> partyList = List.of(party);

    @Test
    public void getNotaryIdentities() {
        when(notaryLookupService.getNotaryIdentities()).thenReturn(partyList);

        List<Party> result = notaryLookupService.getNotaryIdentities();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(partyList);
    }

    @Test
    public void getNotary() {
        CordaX500Name testCordaX500Name = new CordaX500Name("Bob Plc", "Rome", "IT");
        when(notaryLookupService.getNotary(testCordaX500Name)).thenReturn(party);

        Party result = notaryLookupService.getNotary(testCordaX500Name);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void getNotaryWorkers() {
        when(notaryLookupService.getNotaryWorkers(party)).thenReturn(partyList);

        List<Party> result = notaryLookupService.getNotaryWorkers(party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(partyList);
    }

    @Test
    public void isNotary() {
        when(notaryLookupService.isNotary(party)).thenReturn(true);

        Boolean result = notaryLookupService.isNotary(party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
    }


    @Test
    public void getNotaryType() {
        String test = "test";
        when(notaryLookupService.getNotaryType(party)).thenReturn(test);

        String result = notaryLookupService.getNotaryType(party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
