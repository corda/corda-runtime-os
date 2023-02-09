package net.corda.ledger.common.data.transaction.factory

import com.fasterxml.jackson.databind.JsonMappingException
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.getPrivacySalt
import net.corda.ledger.common.testkit.transactionMetadataExample
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WireTransactionFactoryImplTest : CommonLedgerTest() {
    private val metadata = transactionMetadataExample(numberOfComponentGroups = 1)
    private val metadataJson = jsonMarshallingService.format(metadata)
    private val canonicalJson = jsonValidator.canonicalize(metadataJson)
    private val privacySalt = getPrivacySalt()

    @Test
    fun `Creating a very simple WireTransaction`() {
        wireTransactionFactory.create(
            listOf(
                listOf(canonicalJson.toByteArray()),
            ), privacySalt
        )
    }

    @Test
    fun `Creating a WireTransaction with non-canonical metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(metadataJson.toByteArray()),
                ), privacySalt
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
            .hasMessageStartingWith(
                "Transaction metadata representation error:"
            )
    }

    @Test
    fun `Creating a WireTransaction with empty string metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf("".toByteArray()),
                ), privacySalt
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("JSON validation failed due to: \$: unknown found, object expected")
    }

    @Test
    fun `Creating a WireTransaction with empty json metadata throws`() {
        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf("{}".toByteArray()),
                ), privacySalt
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageStartingWith("JSON validation failed due to: \$.ledgerModel: is missing but it is required")
    }

    @Test
    fun `Creating a WireTransaction with mangled json metadata throws`() {
        val mangledJson = canonicalJson.replace("ledgerVersion", "something else")

        assertThatThrownBy {
            wireTransactionFactory.create(
                listOf(
                    listOf(mangledJson.toByteArray()),
                ), privacySalt
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("JSON validation failed due to: \$.ledgerVersion: is missing but it is required")
    }
}