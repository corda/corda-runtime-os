package net.corda.v5.application.crypto;

import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;

class KeyManagementServiceJavaApiTest {

    private final KeyManagementService keyManagementService = mock(KeyManagementService.class);
    private final PublicKey publicKey = mock(PublicKey.class);

    @Test
    void freshKeyTest() {
        Mockito.when(keyManagementService.freshKey()).thenReturn(publicKey);

        Assertions.assertThat(keyManagementService.freshKey()).isNotNull();
        Assertions.assertThat(keyManagementService.freshKey()).isEqualTo(publicKey);
    }

    @Test
    void freshKeyWithExternalIDTest() {
        final UUID externalId = UUID.randomUUID();
        Mockito.when(keyManagementService.freshKey(any())).thenReturn(publicKey);

        Assertions.assertThat(keyManagementService.freshKey(externalId)).isNotNull();
        Assertions.assertThat(keyManagementService.freshKey(externalId)).isEqualTo(publicKey);
    }

    @Test
    void filterMyKeysTest() {
        final List<PublicKey> keys = new ArrayList<>();
        Mockito.when(keyManagementService.filterMyKeys(anyList())).thenReturn(Collections.singleton(publicKey));

        Assertions.assertThat(keyManagementService.filterMyKeys(keys)).isNotEmpty();
        Assertions.assertThat(keyManagementService.filterMyKeys(keys)).isEqualTo(Collections.singleton(publicKey));
    }

    @Test
    void signWithByteArrayTest() {
        final DigitalSignature.WithKey signatureWithKey = new DigitalSignature.WithKey(publicKey, "test".getBytes());
        Mockito.when(keyManagementService.sign((byte[]) any(), any())).thenReturn(signatureWithKey);

        Assertions.assertThat(keyManagementService.sign("test".getBytes(), publicKey)).isNotNull();
        Assertions
                .assertThat(keyManagementService.sign("test".getBytes(), publicKey))
                .isEqualTo(signatureWithKey);
    }

    @Test
    void signWithSignableDataTest() {
        final DigitalSignatureMetadata digitalSignatureMetadata = new DigitalSignatureMetadata(Instant.MIN, new HashMap<>());
        final SecureHash secureHash = new SecureHash("testAlgorithm", new byte[10101]);
        final DigitalSignatureAndMetadata signatureAndMeta =
                new DigitalSignatureAndMetadata(
                        new DigitalSignature.WithKey(publicKey, "test".getBytes()),
                        digitalSignatureMetadata
                );
        SignableData signableData = new SignableData(secureHash, digitalSignatureMetadata);
        Mockito.when(keyManagementService.sign((SignableData) any(), any())).thenReturn(signatureAndMeta);

        Assertions.assertThat(keyManagementService.sign(signableData, publicKey)).isNotNull();
        Assertions
                .assertThat(keyManagementService.sign(signableData, publicKey))
                .isEqualTo(signatureAndMeta);
    }
}
