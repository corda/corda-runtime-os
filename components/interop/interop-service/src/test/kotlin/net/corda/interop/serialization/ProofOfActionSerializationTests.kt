package net.corda.interop.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.common.json.serializers.standardTypesModule
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.flow.application.crypto.SignatureSpecServiceImpl
import net.corda.flow.application.services.impl.interop.ProofOfActionSerialisationModule
import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.proxies.JsonMarshaller
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.ledger.common.flow.impl.transaction.TransactionSignatureServiceImpl
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.ledger.common.testkit.FakePlatformInfoProvider
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.common.testkit.keyPairExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.test.UtxoLedgerWithBatchSignerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.interop.binding.QualifiedWith
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.TimeZone
import kotlin.test.assertEquals

class ProofOfActionSerializationTests : UtxoLedgerWithBatchSignerTest() {

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
                registerModule(ProofOfActionSerialisationModule.module)
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

    @Test
    fun facadeTest() {

        val batchSignatures = realSingingService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()

        val facade =
            FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/proof-of-action-test-facade.json")!!)
        val jsonMapper = object : JsonMarshaller {
            override fun serialize(value: Any): String = jsonMarshallingService.format(value)
            override fun <T : Any> deserialize(value: String, type: Class<T>): T =
                jsonMarshallingService.parse(value, type)
        }
        val dispatcher = TestLockServer().buildDispatcher(facade, jsonMapper)
        val client = facade.getClientProxy<LockFacade>(jsonMapper, MessagingWithoutWebIntermediary(dispatcher))
        val result = client.unlock("not-a-random-string", signature)

        assertEquals(result, "confirmation")
    }

    @Test
    fun facadeTestMessagingLayer() {

        val batchSignatures = realSingingService.signBatch(
            listOf(signedTransaction),
            listOf(publicKeyExample)
        )
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()

        val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/proof-of-action-test-facade.json")!!)
        val jsonMapper = object : JsonMarshaller {
            override fun serialize(value: Any): String = jsonMarshallingService.format(value)
            override fun <T : Any> deserialize(value: String, type: Class<T>): T =
                jsonMarshallingService.parse(value, type)
        }
        val dispatcher = TestLockServer().buildDispatcher(facade, jsonMapper)
        val webServer = WebServer(dispatcher,jsonMarshallingService)
        val webClient = WebClient(webServer, jsonMarshallingService)
        val client = facade.getClientProxy<LockFacade>(jsonMapper, webClient)
        val result = client.unlock("not-a-random-string", signature)

        assertEquals(result, "confirmation")
    }

    val realSingingService = TransactionSignatureServiceImpl(serializationServiceWithWireTx,
        signingService = mock<SigningService>().also {
            whenever(it.findMySigningKeys(any())).thenReturn(mapOf(publicKeyExample to publicKeyExample))
            whenever(
                it.sign(any(), any(), any())
            ).thenReturn(
                DigitalSignatureWithKeyId(
                    publicKeyExample.fullIdHash(),
                    signData("abcdefgsfdsf".toByteArray(), keyPairExample.private)
                    // TODO the method signs hardcoded string only,
                    // try to change to use the actual parameter (byte array) passes to sign method
                )
            )
        },
        signatureSpecService = SignatureSpecServiceImpl(CipherSchemeMetadataImpl()),
        merkleTreeProvider = MerkleTreeProviderImpl(digestService),
        platformInfoProvider = FakePlatformInfoProvider(),
        flowEngine = mock<FlowEngine>().also {
            whenever(it.flowContextProperties).thenReturn(object : FlowContextProperties {
                override fun put(key: String, value: String) {
                    TODO("Not yet implemented")
                }

                override fun get(key: String): String? =
                    when (key) {
                        FlowContextPropertyKeys.CPI_NAME -> "Cordapp1"
                        FlowContextPropertyKeys.CPI_VERSION -> "1"
                        FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH -> "hash1234"
                        // else FlowContextPropertyKeys.CPI_FILE_CHECKSUM
                        else -> "1213213213"
                    }
            })
        },
        transactionSignatureVerificationServiceInternal = mock<TransactionSignatureVerificationServiceInternal>()
    )

    private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature =
            Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }
}

private class MessagingWithoutWebIntermediary(private val inner: (FacadeRequest) -> FacadeResponse) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(facadeRequest: FacadeRequest): FacadeResponse {
        val facadeResponse = inner.invoke(facadeRequest)
        return facadeResponse
    }


}

private class WebClient(private val webClient: (String) -> String,
                                  private val jsonMarshallingService: JsonMarshallingService) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(facadeRequest: FacadeRequest): FacadeResponse {
        val request = jsonMarshallingService.format(facadeRequest)
        val response = webClient.invoke(request)
        val facadeResponse = jsonMarshallingService.parse(response, FacadeResponseImpl::class.java)
        return facadeResponse
    }
}

private class WebServer(private val facadeServer: (FacadeRequest) -> FacadeResponse,
                                        private val jsonMarshallingService: JsonMarshallingService) : (String) -> String {
    override fun invoke(request: String): String {
        val facadeRequest = jsonMarshallingService.parse(request, FacadeRequestImpl::class.java)
        val facadeResponse = facadeServer.invoke(facadeRequest)
        val response = jsonMarshallingService.format(facadeResponse)
        return response
    }
}

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.VALUE_PARAMETER)
@QualifiedWith("org.corda.interop/platform/tokens/types/denomination/1.0")
annotation class Denomination

@BindsFacade("org.corda.interop/platform/lock")  @FacadeVersions("v1.0")
interface LockFacade {

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("create-lock")
    @Suspendable
    fun createLock(assetId: String,
                   recipient: String,
                   @BindsFacadeParameter("notary-keys") notaryKeys: ByteBuffer,
                   @BindsFacadeParameter("draft") draft: String): String
    @FacadeVersions("v1.0")
    @BindsFacadeMethod("unlock")
    @Suspendable
    fun unlock(reservationRef: String,
               @BindsFacadeParameter("signed-tx") proof: DigitalSignatureAndMetadata
    ): String
}

class TestLockServer : LockFacade {

    override fun createLock(assetId: String, recipient: String, notaryKeys: ByteBuffer, draft: String )
        = "not-a-random-string"

    override fun unlock(reservationRef: String, proof: DigitalSignatureAndMetadata): String = "confirmation"

}