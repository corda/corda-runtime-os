package net.corda.v5.cipher.suite;

import net.corda.v5.crypto.SignatureSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignatureSpecEqualityJavaApiTest {
    @Test
    public void apiShouldLookNiceInJava() {
        var spec = new SignatureSpec("SHA256withRSA");
        assertTrue(SignatureSpecEquality.equal(spec, spec));
    }
}
