package net.corda.v5.ledger.utxo;

import net.corda.v5.crypto.SecureHash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.PublicKey;
import java.util.Set;
import java.util.jar.JarInputStream;

public final class AttachmentJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getIdShouldReturnTheExpectedValue() {
        SecureHash value = attachment.getId();
        Assertions.assertEquals(hash, value);
    }

    @Test
    public void getSizeShouldReturnTheExpectedValue() {
        int value = attachment.getSize();
        Assertions.assertEquals(0, value);
    }

    @Test
    public void getSignatoriesShouldReturnTheExpectedValue() {
        Set<PublicKey> value = attachment.getSignatories();
        Assertions.assertEquals(keys, value);
    }

    @Test
    public void extractFileShouldBeCallable() {
        attachment.extractFile("", outputStream);
    }

    @Test
    public void openShouldReturnTheExpectedValue() {
        InputStream value = attachment.open();
        Assertions.assertEquals(inputStream, value);
    }

    @Test
    public void openAsJarShouldReturnTheExpectedValue() {
        JarInputStream value = attachment.openAsJar();
        Assertions.assertEquals(jarInputStream, value);
    }
}
