package net.corda.v5.ledger.consensual;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PartyJavaApiTest {
    private final Party party = mock(Party.class);

    @Test
    public void getName() {
        MemberX500Name testMemberX500Name = new MemberX500Name("Bob Plc", "Rome", "IT");
        when(party.getName()).thenReturn(testMemberX500Name);

        MemberX500Name result = party.getName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testMemberX500Name);
        verify(party, times(1)).getName();
    }

    @Test
    void getOwningKey() {
        final PublicKey publicKey = mock(PublicKey.class);
        Mockito.when(party.getOwningKey()).thenReturn(publicKey);

        PublicKey result = party.getOwningKey();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKey);
        verify(party, times(1)).getOwningKey();
    }
}
