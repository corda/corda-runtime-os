package net.corda.v5.application.crypto;

import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HashingServiceUtilsJavaApiTest {
    private static HashingService digestService;

    @BeforeAll
    public static void setup() {
        digestService = new HashingServiceMock();
    }

    @Test
    public void shouldCreateInstanceOfSecureHash() {
        var str =
                "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A";
        var expectedBytes = new byte[]{
                -65, -41, 108, 14, -69, -48, 6, -2, -27, -125, 65, 5, 71, -63, -120, 123, 2, -110, -66, 118, -43, -126,
                -39, 108, 36, 45, 42, 121, 39, 35, -29, -3, 111, -48, 97, -7, -43, -49, -47, 59, -113, -106, 19, 88,
                -26, -83, -70, 74
        };
        var cut = HashingServiceUtils.parse(digestService, str);
        assertEquals(DigestAlgorithmName.SHA2_384.getName(), cut.getAlgorithm());
        assertArrayEquals(expectedBytes, cut.getBytes());
    }

    static class HashingServiceMock implements HashingService {
        @NotNull
        @Override
        public DigestAlgorithmName getDefaultDigestAlgorithmName() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public SecureHash hash(@NotNull byte[] bytes, @NotNull DigestAlgorithmName digestAlgorithmName) {
            try {
                return new SecureHash(
                        digestAlgorithmName.getName(),
                        MessageDigest.getInstance(digestAlgorithmName.getName()).digest(bytes)
                );
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        @NotNull
        @Override
        public SecureHash hash(@NotNull InputStream inputStream, @NotNull DigestAlgorithmName digestAlgorithmName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int digestLength(@NotNull DigestAlgorithmName digestAlgorithmName) {
            try {
                return MessageDigest.getInstance(digestAlgorithmName.getName()).getDigestLength();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }
}
