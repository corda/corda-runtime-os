package net.corda.v5.ledger.common.transaction;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactionVerificationExceptionJavaApiTest {
    private final Throwable throwable = new Throwable();
    private final TransactionVerificationException transactionVerificationException = new TransactionVerificationException(
            new SecureHash("SHA-256", "123".getBytes()),
            "testMessage",
            throwable);

    @Test
    public void getTxId() {
        SecureHash result = transactionVerificationException.getTxId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(new SecureHash("SHA-256", "123".getBytes()));
    }

    @Test
    public void getMessage() {
        String result = transactionVerificationException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("testMessage");
    }

    @Test
    public void getCause() {
        Throwable result = transactionVerificationException.getCause();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(throwable);
    }

    @Test
    public void setMessage() {
        transactionVerificationException.setMessage("newTestMessage");
        String result = transactionVerificationException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("newTestMessage");
        Assertions.assertThat(result).isNotEqualTo("testMessage");
    }

    @Test
    public void setCause() {
        Throwable newThrowable = new Throwable();
        transactionVerificationException.setCause(newThrowable);
        Throwable result = transactionVerificationException.getCause();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(newThrowable);
        Assertions.assertThat(result).isNotEqualTo(throwable);
    }
}

