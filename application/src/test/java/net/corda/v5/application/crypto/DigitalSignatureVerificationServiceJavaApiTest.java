package net.corda.v5.application.crypto;

import net.corda.v5.base.types.ByteArrays;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.Instant;
import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class DigitalSignatureVerificationServiceJavaApiTest {

    private final DigitalSignatureVerificationService digitalSignatureVerificationService = mock(DigitalSignatureVerificationService.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final SecureHash secureHash256 = SecureHash.create("SHA-256:0123456789ABCDE1");
    private final byte[] byteArray = ByteArrays.parseAsHex("0123456789ABCDE1");
    private final DigitalSignatureAndMetadata digitalSignatureAndMetadata = new DigitalSignatureAndMetadata(
            new DigitalSignature.WithKey(publicKey, byteArray),
            new DigitalSignatureMetadata(Instant.MIN, new HashMap<>())
    );

    @Test
    public void verifySecureHashAndDigitalSignatureAndMeta() throws SignatureException, InvalidKeyException {
        digitalSignatureVerificationService.verify(secureHash256, digitalSignatureAndMetadata);
        verify(digitalSignatureVerificationService, times(1)).verify(secureHash256, digitalSignatureAndMetadata);
    }

    @Test
    public void verifyPublicKeyAndByteArrayAndByteArray() {
        digitalSignatureVerificationService.verify(publicKey, byteArray, byteArray);
        verify(digitalSignatureVerificationService, times(1)).verify(publicKey, byteArray, byteArray);
    }

    @Test
    public void isValidSecureHashAndDigitalSignatureAndMeta() {
        when(digitalSignatureVerificationService.isValid(secureHash256, digitalSignatureAndMetadata)).thenReturn(true);

        boolean result = digitalSignatureVerificationService.isValid(secureHash256, digitalSignatureAndMetadata);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isTrue();
    }


    @Test
    public void isValidPublicKeyAndByteArrayAndByteArray() {
        when(digitalSignatureVerificationService.isValid(publicKey, byteArray, byteArray)).thenReturn(true);

        boolean result = digitalSignatureVerificationService.isValid(publicKey, byteArray, byteArray);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isTrue();
    }
}
