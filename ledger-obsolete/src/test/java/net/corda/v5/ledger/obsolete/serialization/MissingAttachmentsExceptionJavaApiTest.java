package net.corda.v5.ledger.obsolete.serialization;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MissingAttachmentsExceptionJavaApiTest {

    private final SecureHash secureHash = SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<SecureHash> secureHashList = List.of(secureHash);
    private final MissingAttachmentsException missingAttachmentsException = new MissingAttachmentsException(secureHashList, "message");
    private final MissingAttachmentsException missingAttachmentsException2 = new MissingAttachmentsException(secureHashList);

    @Test
    public void getIds() {
        var result = missingAttachmentsException.getIds();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashList);
    }

    @Test
    public void getIdsConstructorWith1Argument() {
        var result = missingAttachmentsException2.getIds();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashList);
    }

    @Test
    public void getMessage() {
        var result = missingAttachmentsException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("message");
    }
}
