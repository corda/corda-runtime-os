package net.corda.ledger.common.data.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.PublicKey

class TransactionMetadataUtilsTest {

    private companion object {
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val magic = JsonMagic
    }

    @Test
    fun `Consume a valid metadata with no header successfully`() {
        val metadata = createTransactionMetadata()
        val jsonBlob = buildJsonByteArrayFromPOJO(metadata)

        val (schemaVersion, json) = magic.consume(jsonBlob)
        assertThat(schemaVersion).isEqualTo(1)
        assertThat(json).isEqualTo(jsonBlob.decodeToString())
    }

    @Test
    fun `Consume metadata byteArray with an invalid header returns null for schemaVersion and empty string for json`() {
        val metadata = createTransactionMetadata()
        val header = "corda".toByteArray() + byteArrayOf(7, 0, 1)
        val jsonBlob = buildJsonByteArrayFromPOJO(metadata, header = header)

        val (schemaVersion, json) = magic.consume(jsonBlob)

        assertThat(schemaVersion).isNull()
        assertThat(json).isEqualTo("")
    }

    @Test
    fun `Consume metadata byteArray with an valid header returns expected schemaVersion and json`() {
        val metadata = createTransactionMetadata()
        val header = "corda".toByteArray() + byteArrayOf(8, 0, 3)
        val jsonBlob = buildJsonByteArrayFromPOJO(metadata, header = header)
        val jsonWithoutHeader = jsonBlob.sliceArray(header.size until jsonBlob.size)
        val (schemaVersion, json) = magic.consume(jsonBlob)

        assertThat(schemaVersion).isEqualTo(3)
        assertThat(json).isEqualTo(jsonWithoutHeader.decodeToString())
    }

    @Test
    fun `Consume metadata byteArray with a header containing opening bracket returns correct schemaVersion and json`() {
        val metadata = createTransactionMetadata()
        val header = "corda".toByteArray() + byteArrayOf(8, 0, 123)
        val jsonBlob = buildJsonByteArrayFromPOJO(metadata, header = header)
        val jsonWithoutHeader = jsonBlob.sliceArray(header.size until jsonBlob.size)
        val (schemaVersion, json) = magic.consume(jsonBlob)

        assertThat(schemaVersion).isEqualTo(123)
        assertThat(json).isEqualTo(jsonWithoutHeader.decodeToString())
    }

    @Test
    fun `Consume invalid metadata byteArray returns null for schemaVersion and empty string for json`() {
        val jsonBlob = "\"something\":\"value\"}".toByteArray()
        val (schemaVersion, json) = magic.consume(jsonBlob)

        assertThat(schemaVersion).isNull()
        assertThat(json).isEqualTo("")
    }

    private fun buildJsonByteArrayFromPOJO(
        transactionMetadata: TransactionMetadata,
        header: ByteArray = "".toByteArray()
    ): ByteArray {
        return header + objectMapper.writeValueAsString(transactionMetadata).toByteArray()
    }

    @Suppress("LongParameterList")
    private fun createTransactionMetadata(
        cpiMetadata: CordaPackageSummaryImpl = cpiPackageSummaryExample,
        cpkPackageSeed: String? = null,
        cpkMetadata: List<CordaPackageSummaryImpl> = cpkPackageSummaryListExample(cpkPackageSeed),
        ledgerModel: String = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
        transactionSubType: String? = null,
        memberShipGroupParametersHash: String? = null,
    ): TransactionMetadataImpl {
        val transactionSubTypePart = if (transactionSubType == null) {
            emptyMap()
        } else {
            mapOf(
                TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to transactionSubType,
            )
        }
        val memberShipGroupParametersHashPart = if (memberShipGroupParametersHash == null) {
            emptyMap()
        } else {
            mapOf(
                TransactionMetadataImpl.MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY to memberShipGroupParametersHash
            )
        }
        val componenGroupStructure = listOf(
            listOf("metadata"),
            listOf(
                MemberX500Name::class.java.name,
                PublicKey::class.java.name,
                "net.corda.v5.ledger.utxo.TimeWindow"
            ),
            listOf(PublicKey::class.java.name),
            listOf("net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent"),
            listOf("CommandInfo"),
            listOf(SecureHash::class.java.name),
            listOf("net.corda.v5.ledger.utxo.StateRef"),
            listOf("net.corda.v5.ledger.utxo.StateRef"),
            listOf("net.corda.v5.ledger.utxo.ContractState"),
            listOf("net.corda.v5.ledger.utxo.Command"),
        )
        return TransactionMetadataImpl(
            mapOf(
                TransactionMetadataImpl.LEDGER_MODEL_KEY to ledgerModel,
                TransactionMetadataImpl.LEDGER_VERSION_KEY to 1,
                TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                TransactionMetadataImpl.PLATFORM_VERSION_KEY to 123,
                TransactionMetadataImpl.CPI_METADATA_KEY to cpiMetadata,
                TransactionMetadataImpl.CPK_METADATA_KEY to cpkMetadata,
                TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
                TransactionMetadataImpl.COMPONENT_GROUPS_KEY to componenGroupStructure,
            ) + transactionSubTypePart + memberShipGroupParametersHashPart
        )
    }
}
