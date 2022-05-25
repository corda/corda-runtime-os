package net.corda.v5.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecureHashJavaApiTest {
    @Test
    public void shouldCreateInstance() {
        var str = "SHA-384:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FD6FD061F9D5CFD13B8F961358E6ADBA4A";
        var expectedBytes = new byte[]{
            -65, -41, 108, 14, -69, -48, 6, -2, -27, -125, 65, 5, 71, -63, -120, 123, 2, -110, -66, 118, -43, -126, -39, 108,
            36, 45, 42, 121, 39, 35, -29, -3, 111, -48, 97, -7, -43, -49, -47, 59, -113, -106, 19, 88, -26, -83, -70, 74
        };
        var cut = SecureHash.create(str);
        assertEquals(DigestAlgorithmName.SHA2_384.getName(), cut.getAlgorithm());
        assertArrayEquals(expectedBytes, cut.getBytes());
    }
}
