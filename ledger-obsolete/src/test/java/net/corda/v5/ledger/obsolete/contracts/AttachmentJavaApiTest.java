package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.util.List;
import java.util.jar.JarInputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AttachmentJavaApiTest {

    private final Attachment attachment = mock(Attachment.class);

    @Test
    public void open() {
        final InputStream inputStream = mock(InputStream.class);
        when(attachment.open()).thenReturn(inputStream);

        final InputStream inputStream1 = attachment.open();

        Assertions.assertThat(inputStream1).isNotNull();
        verify(attachment, times(1)).open();
    }

    @Test
    public void openAsJAR() {
        final JarInputStream jarInputStream = mock(JarInputStream.class);
        when(attachment.openAsJAR()).thenReturn(jarInputStream);

        final InputStream jarInputStream1 = attachment.openAsJAR();

        Assertions.assertThat(jarInputStream1).isNotNull();
        verify(attachment, times(1)).openAsJAR();
    }

    @Test
    public void extractFile() {
        final String path = "path";
        final OutputStream outputStream = mock(OutputStream.class);

        attachment.extractFile(path, outputStream);

        verify(attachment, times(1)).extractFile(path, outputStream);
    }

    @Test
    public void signerKeys() {
        @SuppressWarnings("unchecked")
        final List<PublicKey> signerKeys = mock(List.class);
        when(attachment.getSignerKeys()).thenReturn(signerKeys);

        final List<PublicKey> signerKeys1 = attachment.getSignerKeys();

        Assertions.assertThat(signerKeys1).isNotNull();
        Assertions.assertThat(signerKeys1).isEqualTo(signerKeys);
    }

    @Test
    public void size() {
        final Integer integer = 5;
        when(attachment.getSize()).thenReturn(integer);

        final Integer integer1 = attachment.getSize();

        Assertions.assertThat(integer1).isNotNull();
        Assertions.assertThat(integer1).isEqualTo(integer);
    }
}
