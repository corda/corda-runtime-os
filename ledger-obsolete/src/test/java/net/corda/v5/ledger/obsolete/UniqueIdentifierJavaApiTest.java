package net.corda.v5.ledger.obsolete;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UniqueIdentifierJavaApiTest {
    private final UUID uuid = UUID.randomUUID();
    private final UniqueIdentifier uniqueIdentifier = new UniqueIdentifier("externalId", uuid);

    @Test
    public void getExternalId() {
        var result = uniqueIdentifier.getExternalId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("externalId");
    }

    @Test
    public void getId() {
        var result = uniqueIdentifier.getId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(uuid);
    }

    @Test
    public void to_String() {
        String test = String.format("%s_%s", uniqueIdentifier.getExternalId(), uniqueIdentifier.getId());
        String result = uniqueIdentifier.toString();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }
}
