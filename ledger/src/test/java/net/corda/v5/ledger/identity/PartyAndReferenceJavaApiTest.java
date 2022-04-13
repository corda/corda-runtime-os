package net.corda.v5.ledger.identity;

import net.corda.v5.base.types.OpaqueBytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class PartyAndReferenceJavaApiTest {

    private final AbstractParty abstractParty = mock(AbstractParty.class);
    private final OpaqueBytes opaqueBytes = mock(OpaqueBytes.class);
    private final PartyAndReference partyAndReference = new PartyAndReference(abstractParty, opaqueBytes);

    @Test
    public void party() {
        final AbstractParty abstractParty = partyAndReference.getParty();

        Assertions.assertThat(abstractParty).isNotNull();
        Assertions.assertThat(abstractParty).isEqualTo(this.abstractParty);
    }

    @Test
    public void reference() {
        final OpaqueBytes reference = partyAndReference.getReference();

        Assertions.assertThat(reference).isNotNull();
        Assertions.assertThat(reference).isEqualTo(opaqueBytes);
    }

    @Test
    public void toStringTest() {
        final String toString = partyAndReference.toString();

        Assertions.assertThat(toString).isNotNull();
        Assertions.assertThat(toString).isEqualTo(abstractParty.toString() + opaqueBytes.toString());
    }
}
