package net.corda.crypto.cipher.suite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignatureSpecEqualityJavaApiTest {
    @Test
    public void apiShouldLookNiceInJava() {
        var spec = new SignatureSpecImpl("SHA256withRSA");
        assertTrue(SignatureSpecEquality.equal(spec, spec));
    }
}
