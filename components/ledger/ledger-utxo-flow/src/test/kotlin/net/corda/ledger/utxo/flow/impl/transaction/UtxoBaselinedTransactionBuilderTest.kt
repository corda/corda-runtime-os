package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant

class UtxoBaselinedTransactionBuilderTest : UtxoLedgerTest() {
    private lateinit var utxoBaselinedTransactionBuilder: UtxoBaselinedTransactionBuilder

    @BeforeEach
    fun beforeEach() {
        utxoBaselinedTransactionBuilder = UtxoBaselinedTransactionBuilder(utxoTransactionBuilder)
    }

    @Test
    fun `Overwriting to the same notary does not throw`() {
        utxoBaselinedTransactionBuilder
            .setNotary(utxoNotaryExample)
        assertDoesNotThrow {
            utxoBaselinedTransactionBuilder
                .setNotary(utxoNotaryExample)
        }
    }

    @Test
    fun `Overwriting to different notary throws`() {
        val anotherPublicKey = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public
        utxoBaselinedTransactionBuilder
            .setNotary(utxoNotaryExample)
        assertThatThrownBy {
            utxoBaselinedTransactionBuilder
                .setNotary(
                    Party(
                        MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
                        anotherPublicKey
                    )
                )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Overwriting to the same time window between does not throw`() {
        utxoBaselinedTransactionBuilder
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        assertDoesNotThrow {
            utxoBaselinedTransactionBuilder
                .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        }
    }

    @Test
    fun `Overwriting to a different time window between throws`() {
        utxoBaselinedTransactionBuilder
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        assertThatThrownBy {
            utxoBaselinedTransactionBuilder
                .setTimeWindowBetween(utxoTimeWindowExample.from, Instant.now())
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Overwriting to the same time window until does not throw`() {
        utxoBaselinedTransactionBuilder
            .setTimeWindowUntil(utxoTimeWindowExample.until)
        assertDoesNotThrow {
            utxoBaselinedTransactionBuilder
                .setTimeWindowUntil(utxoTimeWindowExample.until)
        }
    }

    @Test
    fun `Overwriting to a different time window until throws`() {
        utxoBaselinedTransactionBuilder
            .setTimeWindowUntil(utxoTimeWindowExample.until)
        assertThatThrownBy {
            utxoBaselinedTransactionBuilder
                .setTimeWindowUntil(Instant.now())
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `toSignedTransaction() throws`() {
        assertThatThrownBy {
            utxoBaselinedTransactionBuilder
                .toSignedTransaction()
        }.isInstanceOf(UnsupportedOperationException::class.java)
    }

}