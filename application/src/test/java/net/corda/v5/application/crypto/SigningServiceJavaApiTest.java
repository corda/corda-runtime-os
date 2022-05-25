package net.corda.v5.application.crypto;

import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SignatureSpec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

class SigningServiceJavaApiTest {

    private final SigningService signingService = mock(SigningService.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final SignatureSpec spec = new SignatureSpec("mock", null, null);

    @Test
    void signWithByteArrayAndSignatureSpecTest() {
        final DigitalSignature.WithKey signatureWithKey = new DigitalSignature.WithKey(
            publicKey,
            "test".getBytes(),
            new HashMap<>()
        );
        Mockito.when(signingService.sign(any(), any(), any(SignatureSpec.class))).thenReturn(signatureWithKey);

        Assertions.assertThat(signingService.sign("test".getBytes(), publicKey, spec)).isNotNull();
        Assertions
                .assertThat(signingService.sign("test".getBytes(), publicKey, spec))
                .isEqualTo(signatureWithKey);
    }
}
