package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.ledger.common.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.mockito.Mockito.mock;

public class PartyJavaApiTest {

    private final MemberX500Name name = new MemberX500Name("Bob Plc", "Rome", "IT");
    private final PublicKey key = mock(PublicKey.class);
    private final Party party = new Party(name, key);

    @Test
    public void getName() {
        MemberX500Name result = party.getName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(name);
    }

    @Test
    void getOwningKey() {
        PublicKey result = party.getOwningKey();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(key);
    }
}
