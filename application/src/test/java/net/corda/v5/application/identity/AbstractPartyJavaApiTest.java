package net.corda.v5.application.identity;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.base.types.OpaqueBytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

class AbstractPartyJavaApiTest {

    private final AbstractParty abstractParty = mock(AbstractParty.class);

    @Test
    void owningKeyTest() {
        final PublicKey publicKey = mock(PublicKey.class);
        Mockito.when(abstractParty.getOwningKey()).thenReturn(publicKey);

        Assertions.assertThat(abstractParty.getOwningKey()).isNotNull();
        Assertions.assertThat(abstractParty.getOwningKey()).isEqualTo(publicKey);
    }

    @Test
    void nameOrNullIsNullTest() {
        Mockito.when(abstractParty.nameOrNull()).thenReturn(null);

        Assertions.assertThat(abstractParty.nameOrNull()).isNull();
    }

    @Test
    void nameOrNullTest() {
        final MemberX500Name x500Name = MemberX500Name.parse("O=Bank A, L=New York, C=US");
        Mockito.when(abstractParty.nameOrNull()).thenReturn(x500Name);

        Assertions.assertThat(abstractParty.nameOrNull()).isNotNull();
        Assertions.assertThat(abstractParty.nameOrNull()).isEqualTo(x500Name);
    }

    @Test
    void refForOpaqueBytesTest() {
        final OpaqueBytes bytes = OpaqueBytes.of("test".getBytes());
        final Party party = mock(Party.class);
        final PartyAndReference partyAndReference = new PartyAndReference(party, bytes);

        Mockito.when(abstractParty.ref((OpaqueBytes) any())).thenReturn(partyAndReference);

        Assertions.assertThat(abstractParty.ref(bytes)).isNotNull();
        Assertions.assertThat(abstractParty.ref(bytes)).isEqualTo(partyAndReference);
    }

    @Test
    void refForBytesTest() {
        final byte[] bytes = "test".getBytes();
        final Party party = mock(Party.class);
        final PartyAndReference partyAndReference = new PartyAndReference(party, OpaqueBytes.of(bytes));

        Mockito.when(abstractParty.ref(bytes)).thenReturn(partyAndReference);

        Assertions.assertThat(abstractParty.ref(bytes)).isNotNull();
        Assertions.assertThat(abstractParty.ref(bytes)).isEqualTo(partyAndReference);
    }
}
