package net.corda.crypto.cipher.suite;

import net.corda.v5.crypto.SignatureSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SignatureSpecUtilsJavaApiTest {
    @Test
    public void shouldCallGetParamsSafely() {
        var spec = new SignatureSpec("SHA256withECDSA");
        Assertions.assertNull(SignatureSpecUtils.getParamsSafely(spec));
    }
}
