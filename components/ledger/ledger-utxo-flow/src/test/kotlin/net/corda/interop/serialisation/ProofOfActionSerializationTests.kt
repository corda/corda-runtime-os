@file:Suppress("WildcardImport")
package net.corda.interop.serialisation

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.flow.application.services.impl.interop.*
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.*
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.*
import kotlin.test.assertEquals

class ProofOfActionSerializationTests : UtxoLedgerTest() {
    companion object {

        private lateinit var signedTransaction: UtxoSignedTransactionInternal

        private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        private val notaryNode1PublicKey = kpg.generateKeyPair().public
        private val notaryNode2PublicKey = kpg.generateKeyPair().public
        private val notaryKey =
            CompositeKeyProviderImpl().createFromKeys(listOf(notaryNode1PublicKey, notaryNode2PublicKey), 1).also {
                println(it)
            }
        private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
        private val notary = notaryX500Name

        private val jsonMapper =
            JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule())
                enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                setTimeZone(TimeZone.getTimeZone("UTC"))
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(standardTypesModule())
                /* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! */
                /* Here is System under the test : ProofOfActionSerialisationModule.module */
                registerModule(ProofOfActionSerialisationModule.module)
            }
    }

    @BeforeEach
    fun beforeEach() {
        val notaryInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(notaryX500Name)
            whenever(it.publicKey).thenReturn(notaryKey)
        }
        whenever(mockNotaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory,
            mockNotaryLookup
        )
            .setNotary(notary)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(anotherPublicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal
    }

    @Test
    fun secureHash() {
        val by: SecureHash = getSignatureWithMetadataExample(notaryNode1PublicKey).by
        val json = jsonMapper.writeValueAsString(by)
        val deserializeObject = jsonMapper.readValue(json, SecureHash::class.java)
        assertEquals(by, deserializeObject)
    }

    @Test
    fun digitalSignatureWithKeyId() {
        val signature: DigitalSignature.WithKeyId = getSignatureWithMetadataExample(notaryNode1PublicKey).signature
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignature.WithKeyId::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadataWithoutProof() {
        val signature: DigitalSignatureAndMetadata = getSignatureWithMetadataExample(notaryNode1PublicKey)
        assertNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadata() {
        val batchSignatures = realSingingService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()
        assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializedObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        assertEquals(signature, deserializedObject)
    }

    @Test
    fun testWithoutProofOfActionModule() {
        // Mapper without ProofOfActionSerialisationModule.module
        val jsonMapper =
            JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
                registerModule(KotlinModule.Builder().build())
                registerModule(JavaTimeModule())
                enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                setTimeZone(TimeZone.getTimeZone("UTC"))
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(standardTypesModule())
            }
        val batchSignatures = realSingingService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()
        assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        assertThrowsExactly(com.fasterxml.jackson.databind.exc.InvalidDefinitionException::class.java) {
            jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        }
    }
}
