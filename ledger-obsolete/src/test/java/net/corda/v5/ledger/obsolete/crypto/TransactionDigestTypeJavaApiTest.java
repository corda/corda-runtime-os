package net.corda.v5.ledger.obsolete.crypto;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactionDigestTypeJavaApiTest {

    @Test
    public void transactionDigestTypeTree() {
        String result = TransactionDigestType.TREE;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("TREE");
    }

    @Test
    public void transactionDigestTypeComponenthash() {
        String result = TransactionDigestType.COMPONENTHASH;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("COMPONENTHASH");
    }

    @Test
    public void transactionDigestTypeComponentnonce() {
        String result = TransactionDigestType.COMPONENTNONCE;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("COMPONENTNONCE");
    }

    @Test
    public void transactionDigestTypeComponentnoncehash() {
        String result = TransactionDigestType.COMPONENTNONCEHASH;

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("COMPONENTNONCEHASH");
    }
}
