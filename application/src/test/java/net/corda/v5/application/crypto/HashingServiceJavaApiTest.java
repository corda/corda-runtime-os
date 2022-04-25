package net.corda.v5.application.crypto;

import net.corda.v5.base.types.ByteArrays;
import net.corda.v5.base.types.OpaqueBytes;
import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static net.corda.v5.crypto.DigestAlgorithmName.SHA2_256;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HashingServiceJavaApiTest {

    private final HashingService hashingService = mock(HashingService.class);

    private final String VALUE_SHA256_A = "6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A";
    private final SecureHash secureHash256 = SecureHash.create("SHA-256:" + VALUE_SHA256_A);

    @Test
    public void getDefaultDigestAlgorithmName() {
        when(hashingService.getDefaultDigestAlgorithmName()).thenReturn(SHA2_256);

        DigestAlgorithmName result = hashingService.getDefaultDigestAlgorithmName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(SHA2_256);
    }

    @Test
    public void hashByteArray() {
        byte[] bytes = ByteArrays.parseAsHex(VALUE_SHA256_A);
        when(hashingService.hash(bytes, SHA2_256)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(bytes, SHA2_256);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }
}
