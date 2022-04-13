package net.corda.v5.application.serialization;

import net.corda.v5.serialization.SerializedBytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SerializationServiceJavaApiTest {

    private final SerializationService serializationService = mock(SerializationService.class);
    private final Object object = new Object();

    @Test
    public void serialize() {
        final SerializedBytes<Object> serializeBytes = new SerializedBytes<>(object.toString().getBytes());
        when(serializationService.serialize(object)).thenReturn(serializeBytes);

        final SerializedBytes<Object> serializedBytes = serializationService.serialize(object);

        Assertions.assertThat(serializedBytes).isNotNull();
        Assertions.assertThat(serializedBytes).isEqualTo(serializeBytes);
        verify(serializationService, times(1)).serialize(object);
    }

    @Test
    public void deserializeSerializedBytes() {
        final SerializedBytes<Object> serializeBytes = new SerializedBytes<>(object.toString().getBytes());
        when(serializationService.deserialize(serializeBytes, Object.class)).thenReturn(object);

        final Object obj = serializationService.deserialize(serializeBytes, Object.class);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(object);
        verify(serializationService, times(1)).deserialize(serializeBytes, Object.class);
    }

    @Test
    public void deserializeByteArray() {
        final byte[] serializeBytes = object.toString().getBytes();
        when(serializationService.deserialize(serializeBytes, Object.class)).thenReturn(object);

        final Object obj = serializationService.deserialize(serializeBytes, Object.class);

        Assertions.assertThat(obj).isNotNull();
        Assertions.assertThat(obj).isEqualTo(object);
        verify(serializationService, times(1)).deserialize(serializeBytes, Object.class);
    }
}
