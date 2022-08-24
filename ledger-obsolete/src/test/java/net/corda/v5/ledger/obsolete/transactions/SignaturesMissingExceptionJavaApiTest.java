package net.corda.v5.ledger.obsolete.transactions;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class SignaturesMissingExceptionJavaApiTest {

    private final PublicKey publicKey = mock(PublicKey.class);{
        when(publicKey.getEncoded()).thenReturn(new byte[1998]);
    }
    private final Set<PublicKey> publicKeys = Set.of(publicKey);
    private final List<String> descriptions = List.of("some descriptions");
    private final SecureHash secureHash = SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final SignaturesMissingException exception = new SignaturesMissingException(publicKeys, descriptions, secureHash);

    @Test
    public void getId() {
        SecureHash result = exception.getId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash);
    }

    @Test
    public void getMissing() {
        Set<PublicKey> result = exception.getMissing();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKeys);
    }

    @Test
    public void getDescriptions() {
        List<String> result = exception.getDescriptions();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(descriptions);
    }
}
