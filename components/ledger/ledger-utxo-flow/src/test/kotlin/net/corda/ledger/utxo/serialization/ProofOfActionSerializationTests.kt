package net.corda.ledger.utxo.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.application.impl.services.json.ProofOfActionSerialisationModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.crypto.cipher.suite.merkle.MerkleProofProvider
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.*

class ProofOfActionSerializationTests : UtxoLedgerTest() {

    companion object {

        private lateinit var signedTransaction: UtxoSignedTransactionInternal

        private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        private val notaryNode1PublicKey = kpg.generateKeyPair().public

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
                registerModule(ProofOfActionSerialisationModule(object : MerkleProofProvider {
                    override fun createMerkleProof(
                        proofType: MerkleProofType,
                        treeSize: Int,
                        leaves: List<IndexedMerkleLeaf>,
                        hashes: List<SecureHash>
                    ): MerkleProof {
                        TODO("Not yet implemented")
                    }

                    override fun createIndexedMerkleLeaf(
                        index: Int,
                        nonce: ByteArray?,
                        leafData: ByteArray
                    ): IndexedMerkleLeaf {
                        TODO("Not yet implemented")
                    }
                }).module)
            }
    }

    @BeforeEach
    fun beforeEach() {
        val notaryInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(notaryX500Name)
            whenever(it.publicKey).thenReturn(publicKeyExample)
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
        Assertions.assertEquals(by, deserializeObject)
    }

    @Test
    fun digitalSignatureWithKeyId() {
        val signature: DigitalSignature.WithKeyId = getSignatureWithMetadataExample(notaryNode1PublicKey).signature
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignature.WithKeyId::class.java)
        Assertions.assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadataWithoutProof() {
        val signature: DigitalSignatureAndMetadata = getSignatureWithMetadataExample(notaryNode1PublicKey)
        Assertions.assertNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializeObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        Assertions.assertEquals(signature, deserializeObject)
    }

    @Test
    fun digitalSignatureAndMetadata() {
        val batchSignatures = transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()
        Assertions.assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        val deserializedObject = jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        Assertions.assertEquals(signature, deserializedObject)
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
        Assertions.assertNotNull(signature.proof)
        val json = jsonMapper.writeValueAsString(signature)
        Assertions.assertThrowsExactly(InvalidDefinitionException::class.java) {
            jsonMapper.readValue(json, DigitalSignatureAndMetadata::class.java)
        }
    }
}