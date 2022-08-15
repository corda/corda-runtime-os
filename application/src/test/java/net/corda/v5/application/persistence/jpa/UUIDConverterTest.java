package net.corda.v5.application.persistence.jpa;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UUIDConverterTest {
    @Test
    public void CanConvertToUUID() {
        UUID uuid = UUID.randomUUID();

        UUID actual = new UUIDConverter().convertToEntityAttribute(uuid.toString());

        Assertions.assertThat(actual).isEqualTo(uuid);
    }

    @Test
    public void CanConvertToString() {
        UUID uuid = UUID.randomUUID();

        String actual = new UUIDConverter().convertToDatabaseColumn(uuid);

        Assertions.assertThat(actual).isEqualTo(uuid.toString());
    }
}
