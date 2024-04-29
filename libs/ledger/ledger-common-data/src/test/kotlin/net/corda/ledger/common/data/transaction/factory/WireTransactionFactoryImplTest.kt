package net.corda.ledger.common.data.transaction.factory

import com.fasterxml.jackson.databind.JsonMappingException
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.transactionMetadataExample
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@Suppress("MaxLineLength")
class WireTransactionFactoryImplTest : CommonLedgerTest() {
    private val metadata = transactionMetadataExample()
    private val metadataJson = jsonMarshallingService.format(metadata)
    private val canonicalJson = jsonValidator.canonicalize(metadataJson)
    private val privacySalt = mockPrivacySaltProviderService.generatePrivacySalt()
    private val header = "corda".toByteArray() + byteArrayOf(8, 0)

    @Test
    fun `Creating a very simple WireTransaction`() {
        val componentGroupLists = (1..10).map {
            listOf(canonicalJson.toByteArray())
        }
        wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
        )
    }

    @Test
    fun `Creating a WireTransaction with non-canonical metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(metadataJson.toByteArray()),
                ),
                privacySalt
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
                privacySalt
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Metadata is empty.")
    }

    @Test
    fun `Creating a WireTransaction with empty json metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf("{}".toByteArray()),
                ),
                privacySalt
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
                privacySalt
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
                privacySalt
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
                privacySalt
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
        val componentGroupLists = (1..10).map {
            listOf(canonicalJson.toByteArray())
        }
        wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
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
                privacySalt
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
        val componentGroupLists = (1..10).map {
            listOf(canonicalJson.toByteArray())
        }
        wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
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
                privacySalt
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
                privacySalt
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
                privacySalt
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
                privacySalt
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ledgerModel: does not have a value in the enumeration")
    }

    @Test
    fun `Creating a WireTransaction parses metadata with a header successfully`() {
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = "Membership group parameters hash"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        val componentGroupLists = (1..10).map {
            listOf((header + canonicalJson.toByteArray()))
        }
        wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
        )
    }

    @Test
    fun `Creating a WireTransaction parses metadata with a header successfullyeee`() {
        val header = "corda".toByteArray() + byteArrayOf(8, 123)
        val metadata = transactionMetadataExample(
            ledgerModel = "net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl",
            transactionSubType = "GENERAL",
            memberShipGroupParametersHash = "Membership group parameters hash"
        )
        val metadataJson = jsonMarshallingService.format(metadata)
        val canonicalJson = jsonValidator.canonicalize(metadataJson)
        val componentGroupLists = (1..10).map {
            listOf((header + canonicalJson.toByteArray()))
        }
        wireTransactionFactory.create(
            componentGroupLists,
            privacySalt
        )
    }
}
