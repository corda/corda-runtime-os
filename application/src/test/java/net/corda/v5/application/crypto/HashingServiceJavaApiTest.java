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
    private final String VALUE_SHA256_B = "6B1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581B";
    private final String VALUE_SHA256_C = "6C1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581C";
    private final SecureHash secureHash256 = SecureHash.create("SHA-256:" + VALUE_SHA256_A);
    private final SecureHash secureHash256B = SecureHash.create("SHA-256:" + VALUE_SHA256_B);
    private final SecureHash secureHash256C = SecureHash.create("SHA-256:" + VALUE_SHA256_C);

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
        when(hashingService.hash(bytes)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(bytes);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void hashOpaqueBytesAndDigestAlgorithmName() {
        OpaqueBytes opaqueBytes = OpaqueBytes.of(VALUE_SHA256_A.getBytes(StandardCharsets.UTF_8));
        when(hashingService.hash(opaqueBytes, SHA2_256)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(opaqueBytes, SHA2_256);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void hashOpaqueBytes() {
        OpaqueBytes opaqueBytes = OpaqueBytes.of(VALUE_SHA256_A.getBytes(StandardCharsets.UTF_8));
        when(hashingService.hash(opaqueBytes)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(opaqueBytes);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void hashStringAndDigestAlgorithmName() {
        when(hashingService.hash(VALUE_SHA256_A, SHA2_256)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(VALUE_SHA256_A, SHA2_256);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void hashString() {
        when(hashingService.hash(VALUE_SHA256_A)).thenReturn(secureHash256);

        SecureHash result = hashingService.hash(VALUE_SHA256_A);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void reHash() {
        when(hashingService.reHash(secureHash256)).thenReturn(secureHash256B);

        SecureHash result = hashingService.reHash(secureHash256);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256B);
    }


    @Test
    public void randomHash() {
        when(hashingService.randomHash(SHA2_256)).thenReturn(secureHash256);

        SecureHash result = hashingService.randomHash(SHA2_256);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }

    @Test
    public void concatenate() {
        when(hashingService.concatenate(secureHash256, secureHash256B)).thenReturn(secureHash256C);

        SecureHash result = hashingService.concatenate(secureHash256, secureHash256B);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256C);
    }
}
