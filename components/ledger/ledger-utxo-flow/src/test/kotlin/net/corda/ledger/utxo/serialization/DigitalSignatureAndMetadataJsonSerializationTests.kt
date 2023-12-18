package net.corda.ledger.utxo.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.application.impl.services.json.DigitalSignatureAndMetadataSerialisationModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.merkle.impl.MerkleProofProviderImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertContains

/**
 * The test requires a transaction signature with Merkle Proof.
 * Such signature can be produced by the base class 'UtxoLedgerTest' in this module ('components/ledger/ledger-utxo-flow').
 * The test requires a service implementation of 'MerkleProofProvider' from 'libs/crypto/merkle-impl'. The service
 * is injected into the SUT (System Under Test).
 * The actual SUT class 'DigitalSignatureAndMetadataSerialisationModule' is from 'libs/application/application-impl' module,
 * so the test is not placed in the same module as the class.
 * There are two reasons:
 * 1) 'libs/application/application-impl' module has no test dependencies on either
 * 'testing/ledger/ledger-utxo-base-test' nor 'libs/crypto/merkle-impl', placing test here required new test dependency on
 * 'libs/crypto/merkle-impl' module only.
 * 2) If the test was run from withing a module other than 'components/ledger',
 * then it threw an exception that Sandbox cannot be mocked.
 *
 * Placing the test inside here seemed like a reasonable compromise.
 */

class DigitalSignatureAndMetadataJsonSerializationTests : UtxoLedgerTest() {

    private val signedTransaction: UtxoSignedTransactionInternal =
        UtxoTransactionBuilderImpl(utxoSignedTransactionFactory, mockNotaryLookup)
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(anotherPublicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal

    companion object {
        // Clone of SecureHash serializer/deserializer from JsonMarshallingServiceImpl call which are not exposed outside,
        // however they work together with SUT 'ProofOfActionSerialisationModule.module'.
        private val serializersFromJsonMarshallingService = SimpleModule().apply {
            this.addSerializer(
                SecureHash::class.java,
                object : JsonSerializer<SecureHash>() {
                    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
                        generator.writeString(obj.toString())
                    }
                }
            )
            this.addDeserializer(
                SecureHash::class.java,
                object : JsonDeserializer<SecureHash>() {
                    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash =
                        parseSecureHash(parser.text)
                }
            )
        }

        private val mapperWithoutProofOfActionModule =
            JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule())
                enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                setTimeZone(TimeZone.getTimeZone("UTC"))
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(standardTypesModule())
                // Here is a partial System under test (SUT):
                registerModule(serializersFromJsonMarshallingService)
            }

        private val mapper = mapperWithoutProofOfActionModule.copy().apply {
            // Here is System under test 'ProofOfActionSerialisationModule.module':
            registerModule(DigitalSignatureAndMetadataSerialisationModule(MerkleProofProviderImpl()).module)
        }
    }

    @Test
    fun secureHash() {
        val by: SecureHash = getSignatureWithMetadataExample().by
        val json: String = mapper.writeValueAsString(by)
        val deserializeObject = mapper.readValue(json, SecureHash::class.java)
        assertEquals(by, deserializeObject)
    }

    @Test
    fun digitalSignatureWithKeyId() {
        val signature: DigitalSignature.WithKeyId = getSignatureWithMetadataExample().signature
        val json: String = mapper.writeValueAsString(signature)
        val deserializeObject = mapper.readValue(json, DigitalSignature.WithKeyId::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun `digitalSignatureAndMetadata without Merkle proof`() {
        val signature: DigitalSignatureAndMetadata = getSignatureWithMetadataExample()
        assertNull(signature.proof)
        val json: String = mapper.writeValueAsString(signature)
        val deserializeObject = mapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun `digitalSignatureAndMetadata with Merkle proof`() {
        val signatureBatches =
            transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        assertEquals(1, signatureBatches.size)
        val signatureBatch = signatureBatches.first()
        assertEquals(1, signatureBatch.size)
        val signature: DigitalSignatureAndMetadata = signatureBatch.first()
        assertNotNull(signature.proof)
        val json: String = mapper.writeValueAsString(signature)
        assertContains(json, "signature") // just double check that string is non empty
        assertContains(json, "metadata")
        assertContains(json, "proof")
        val deserializedObject = mapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializedObject)
    }

    @Test
    fun `deserialization fails without ProofOfActionSerialisation module`() {
        val signatureBatches =
            transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        assertEquals(1, signatureBatches.size)
        val signatureBatch = signatureBatches.first()
        assertEquals(1, signatureBatch.size)
        val signature: DigitalSignatureAndMetadata = signatureBatch.first()
        assertNotNull(signature.proof)
        val jsonString = mapperWithoutProofOfActionModule.writeValueAsString(signature)
        assertThrowsExactly(InvalidDefinitionException::class.java) {
            mapperWithoutProofOfActionModule.readValue(jsonString, DigitalSignatureAndMetadata::class.java)
        }
    }

    @Test
    fun `deserialization of an empty string fails`() {
        assertThrowsExactly(MismatchedInputException::class.java) {
            mapper.readValue("", DigitalSignatureAndMetadata::class.java)
        }
    }
}
