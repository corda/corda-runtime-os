package net.corda.ledger.common.data.transaction.factory

import com.fasterxml.jackson.databind.JsonMappingException
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.transactionMetadataExample
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class WireTransactionFactoryImplTest : CommonLedgerTest() {
    private val metadata = transactionMetadataExample()
    private val metadataJson = jsonMarshallingService.format(metadata)
    private val canonicalJson = jsonValidator.canonicalize(metadataJson)

    @BeforeEach
    fun setup() {
        val flowId = "fc321a0c-62c6-41a1-85e6-e61870ab93aa"
        val suspendCount = 10

        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint

        whenever(checkpoint.flowId).thenReturn(flowId)
        whenever(checkpoint.suspendCount).thenReturn(suspendCount)
    }

    @Test
    fun `transaction ids are deterministic`() {
        val transaction1 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        val transaction2 = wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )

        assertThat(transaction1.id).isEqualTo(transaction2.id)
        assertThat(transaction1.privacySalt).isEqualTo(transaction2.privacySalt)
    }

    @Test
    fun `Creating a very simple WireTransaction`() {
        wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )
    }

    @Test
    fun `Creating a WireTransaction with non-canonical metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(metadataJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageStartingWith("Expected to receive canonical JSON but got:")
    }

    @Test
    fun `Creating a TransactionMetadata with empty metadata throws`() {
        assertThatThrownBy {
            val metadata = TransactionMetadataImpl(mapOf())
            jsonMarshallingService.format(metadata)
        }
            .isInstanceOf(JsonMappingException::class.java)
    }

    @Test
    fun `Creating a WireTransaction with empty string metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf("".toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageStartingWith("JSON validation failed due to:")
    }

    @Test
    fun `Creating a WireTransaction with empty json metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf("{}".toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is missing but it is required")
    }

    @Test
    fun `Creating a WireTransaction with mangled json metadata throws`() {
        val mangledJson = canonicalJson.replace("ledgerVersion", "something else")

        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(mangledJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ledgerVersion: is missing but it is required")
    }

    @Test
    fun `Creating a WireTransaction with unknown json metadata properties throws`() {
        val mangledJson = canonicalJson.replaceFirst("{", "{\"aaa\":\"value\",")

        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(mangledJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("the schema does not allow additional properties")
    }

    @Test
    fun `Creating a WireTransaction without CPK metadata throws`() {
        val metadata = transactionMetadataExample(cpkMetadata = emptyList())
        val metadataJson = jsonMarshallingService.format(metadata)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(metadataJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cpkMetadata: there must be a minimum of 1 items in the array")
    }

    @Test
    fun `Creating a WireTransaction with Consensual settings`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
            transactionSubType = null
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )
    }

    @Test
    fun `Creating a WireTransaction with Consensual settings with transaction subtype throws`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
            transactionSubType = "GENERAL"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(canonicalJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("transactionSubtype: does not have a value in the enumeration")
    }

    @Test
    fun `Creating a WireTransaction with Utxo settings`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = "Membership group parameters hash"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ),
            privacySaltProviderService.generatePrivacySalt()
        )
    }

    @Test
    fun `Creating a WireTransaction with UTXO settings without transaction subtype throws`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = null,
            memberShipGroupParametersHash = "Membership group parameters hash"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(canonicalJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("transactionSubtype: does not have a value in the enumeration")
    }

    @Test
    fun `Creating a WireTransaction with UTXO settings without membership group parameters hash subtype throws`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = null
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        println(canonicalJson)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(canonicalJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("membershipGroupParametersHash: null found, string expected")
    }

    @Test
    fun `Creating a WireTransaction with UTXO settings with unknown transaction subtype throws`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = "UNKNOWN",
            memberShipGroupParametersHash = "Membership group parameters hash"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(canonicalJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("transactionSubtype: does not have a value in the enumeration")
    }

    @Test
    fun `Creating a WireTransaction with unknown ledger model throws`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "unknown"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(canonicalJson.toByteArray()),
                ),
                privacySaltProviderService.generatePrivacySalt()
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ledgerModel: does not have a value in the enumeration")
    }
}
