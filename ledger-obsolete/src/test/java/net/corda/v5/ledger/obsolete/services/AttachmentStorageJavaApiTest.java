package net.corda.v5.ledger.obsolete.services;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.contracts.Attachment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AttachmentStorageJavaApiTest {

    private final AttachmentStorage attachmentStorage = mock(AttachmentStorage.class);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");

    @Test
    public void openAttachment() {
        Attachment attachment = mock(Attachment.class);
        when(attachmentStorage.openAttachment(secureHash)).thenReturn(attachment);

        Attachment result = attachmentStorage.openAttachment(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(attachment);
    }

    @Test
    public void importAttachment() throws IOException {
        InputStream inputStream = mock(InputStream.class);
        when(attachmentStorage.importAttachment(inputStream, "testUploader", "testFilename")).thenReturn(secureHash);

        SecureHash result = attachmentStorage.importAttachment(inputStream, "testUploader", "testFilename");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash);
    }

    @Test
    public void hasAttachment() {
        when(attachmentStorage.hasAttachment(secureHash)).thenReturn(true);

        Boolean result = attachmentStorage.hasAttachment(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);
    }
}
