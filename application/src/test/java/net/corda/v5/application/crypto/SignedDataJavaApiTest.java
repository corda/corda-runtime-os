package net.corda.v5.application.crypto;

import net.corda.v5.application.services.serialization.SerializationService;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SignatureVerificationService;
import net.corda.v5.serialization.SerializedBytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SignatureException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SignedDataJavaApiTest {

    final private SerializationService serializationService = mock(SerializationService.class);
    final private SignatureVerificationService signatureVerificationService = mock(SignatureVerificationService.class);
    final private PublicKey publicKey = mock(PublicKey.class);
    final private byte[] bytes = "test".getBytes(StandardCharsets.UTF_8);
    final private SerializedBytes<String> serializedBytes = new SerializedBytes<>(bytes);
    final private DigitalSignature.WithKey withKey = new DigitalSignature.WithKey(publicKey, bytes);

    @Test
    public void verified() throws SignatureException {
        when(serializationService.deserialize(serializedBytes.getBytes(), Object.class)).thenReturn("test");
        SignedData<String> signedData = new SignedData<>(serializedBytes, withKey);

        String result = signedData.verified(signatureVerificationService, serializationService);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("test");
    }

    @Test
    public void verifyData() {
        @SuppressWarnings("unchecked")
        SignedData<String> signedData = mock(SignedData.class);
        signedData.verifyData("test");
        verify(signedData, times(1)).verifyData("test");
    }

    @Test
    public void getRaw() {
        SignedData<String> signedData = new SignedData<>(serializedBytes, withKey);

        SerializedBytes<String> result = signedData.getRaw();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(serializedBytes);
    }

    @Test
    public void getSig() {
        SignedData<String> signedData = new SignedData<>(serializedBytes, withKey);

        DigitalSignature.WithKey result = signedData.getSig();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(withKey);
    }
}
