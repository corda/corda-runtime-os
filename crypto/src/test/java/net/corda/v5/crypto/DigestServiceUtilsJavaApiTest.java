package net.corda.v5.crypto;

import net.corda.v5.crypto.mocks.DigestServiceMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DigestServiceUtilsJavaApiTest {
    private static DigestService digestService;

    @BeforeAll
    public static void setup() {
        digestService = new DigestServiceMock();
    }

    @Test
    public void shouldCreateInstance() {
        var str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A";
        var expectedBytes = new byte[]{
            -65, -41, 108, 14, -69, -48, 6, -2, -27, -125, 65, 5, 71, -63, -120, 123, 2, -110, -66, 118, -43, -126, -39, 108,
            36, 45, 42, 121, 39, 35, -29, -3, 111, -48, 97, -7, -43, -49, -47, 59, -113, -106, 19, 88, -26, -83, -70, 74
        };
        var cut = DigestServiceUtils.create(digestService, str);
        assertEquals(DigestAlgorithmName.SHA2_384.getName(), cut.getAlgorithm());
        assertArrayEquals(expectedBytes, cut.getBytes());
    }

    @Test
    public void ShouldReturnExpectedZeroHash() {
        var hash = DigestServiceUtils.getZeroHash(digestService, new DigestAlgorithmName("SHA-512"));
        assertEquals("SHA-512", hash.getAlgorithm());
        byte[] expectedBytes = new byte[64];
        Arrays.fill(expectedBytes, (byte) 0);
        assertArrayEquals(expectedBytes, hash.getBytes());
    }

    @Test
    public void ShouldReturnExpectedAllOnesHash() {
        var hash = DigestServiceUtils.getAllOnesHash(digestService, new DigestAlgorithmName("SHA-512"));
        assertEquals("SHA-512", hash.getAlgorithm());
        byte[] expectedBytes = new byte[64];
        Arrays.fill(expectedBytes, (byte) 255);
        assertArrayEquals(expectedBytes, hash.getBytes());
    }
}
