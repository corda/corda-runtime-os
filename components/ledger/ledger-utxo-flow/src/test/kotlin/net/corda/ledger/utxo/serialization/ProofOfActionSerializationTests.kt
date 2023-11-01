package net.corda.ledger.utxo.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.application.impl.services.json.ProofOfActionSerialisationModule
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*


class ProofOfActionSerializationTests : UtxoLedgerTest() {

    companion object {

        private lateinit var signedTransaction: UtxoSignedTransactionInternal

        private val jsonMapper =
            JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule())
                enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                setTimeZone(TimeZone.getTimeZone("UTC"))
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(standardTypesModule())

                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // Here is system under the test 'ProofOfActionSerialisationModule.module'
                // and SecureHash serializer clone from JsonMarshallingServiceImpl
                // (JsonMarshallingServiceImpl doesn't expose them outside)
                registerModule(ProofOfActionSerialisationModule(MerkleProofProviderImpl()).module)
                registerModule(SimpleModule().apply {
                    this.addSerializer(SecureHash::class.java,
                        object : JsonSerializer<SecureHash>() {
                            override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
                                generator.writeString(obj.toString())
                            }
                        }
                    )
                    this.addDeserializer(SecureHash::class.java,
                        object : JsonDeserializer<SecureHash>() {
                            override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash =
                                parseSecureHash(parser.text)
                        }
                    )
                })
            }
    }

    @BeforeEach
    fun beforeEach() {
        signedTransaction = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory, mockNotaryLookup)
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(anotherPublicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal
    }

    @Test
    fun secureHash() {
        val by: SecureHash = getSignatureWithMetadataExample().by
        val json = jsonMapper.writeValueAsString(by)
        val deserializeObject = jsonMapper.readValue(json, SecureHash::class.java)
        assertEquals(by, deserializeObject)
    }

    @Test
    fun digitalSignatureWithKeyId() {
        val signature: DigitalSignature.WithKeyId = getSignatureWithMetadataExample().signature
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignature.WithKeyId::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadataWithoutProof() {
        val signature: DigitalSignatureAndMetadata = getSignatureWithMetadataExample()
        assertNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadata() {
        val batchSignatures = transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()
        assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializedObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializedObject)
    }

    @Test
    fun testWithoutProofOfActionModule() {
        // Mapper without ProofOfActionSerialisationModule.module
        val jsonMapper = JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            setTimeZone(TimeZone.getTimeZone("UTC"))
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(standardTypesModule())
        }
        val batchSignatures = transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()
        assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        assertThrowsExactly(InvalidDefinitionException::class.java) {
            jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        }
    }
}