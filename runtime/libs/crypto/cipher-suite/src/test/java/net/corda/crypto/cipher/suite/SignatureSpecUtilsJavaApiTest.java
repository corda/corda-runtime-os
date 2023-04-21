package net.corda.crypto.cipher.suite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SignatureSpecUtilsJavaApiTest {
    @Test
    public void shouldCallGetParamsSafely() {
        var spec = new SignatureSpecImpl("SHA256withECDSA");
        Assertions.assertNull(SignatureSpecUtils.getParamsSafely(spec));
    }
}
