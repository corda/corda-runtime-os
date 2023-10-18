@file:Suppress("WildcardImport")
package net.corda.interop.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.flow.application.services.impl.interop.*
import net.corda.flow.application.services.impl.interop.dispatch.buildDispatcher
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.facade.FacadeRequestImpl
import net.corda.flow.application.services.impl.interop.facade.FacadeResponseImpl
import net.corda.flow.application.services.impl.interop.proxies.JsonMarshaller
import net.corda.flow.application.services.impl.interop.proxies.getClientProxy
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.*
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.interop.binding.*
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.TimeZone
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

    @Test
    fun facadeTest() {

        val batchSignatures = realSingingService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()

        val facade =
            FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/locking-facade.json")!!)
        val jsonMapper = object : JsonMarshaller {
            override fun serialize(value: Any): String = jsonMarshallingService.format(value)
            override fun <T : Any> deserialize(value: String, type: Class<T>): T =
                jsonMarshallingService.parse(value, type)
        }
        val dispatcher = TestLockServer().buildDispatcher(facade, jsonMapper)
        val client = facade.getClientProxy<LockFacade>(jsonMapper, MessagingWithoutWebIntermediary(dispatcher))
        val result = client.unlock("not-a-random-string", signature, ByteBuffer.wrap(byteArrayOf(65, 66, 67, 68)))

        assertEquals(result, "confirmation")
    }

    @Test
    fun facadeTestMessagingLayer() {

        val batchSignatures = realSingingService.signBatch(
            listOf(signedTransaction),
            listOf(publicKeyExample)
        )
        val signature: DigitalSignatureAndMetadata = batchSignatures.first().first()

        val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/locking-facade.json")!!)
        val jsonMapper = object : JsonMarshaller {
            override fun serialize(value: Any): String = jsonMarshallingService.format(value)
            override fun <T : Any> deserialize(value: String, type: Class<T>): T =
                jsonMarshallingService.parse(value, type)
        }
        val dispatcher = TestLockServer().buildDispatcher(facade, jsonMapper)
        val webServer = WebServer(dispatcher,jsonMarshallingService)
        val webClient = WebClient(webServer, jsonMarshallingService)
        val client = facade.getClientProxy<LockFacade>(jsonMapper, webClient)
        val result = client.unlock("not-a-random-string", signature, ByteBuffer.wrap(byteArrayOf(65, 66, 67, 68)))

        assertEquals(result, "confirmation")
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
    fun createLock(@Denomination denomination: String,
                   amount: BigDecimal,
                   otherParty: String,
                   @BindsFacadeParameter("notary-keys") notaryKeys: String,
                   @BindsFacadeParameter("draft") draft: String) : String

    @FacadeVersions("v1.0")
    @BindsFacadeMethod("unlock")
    @Suspendable
    fun unlock(reservationRef: String,
               @BindsFacadeParameter("signed-tx") proof: DigitalSignatureAndMetadata,
               key: ByteBuffer) : String
}

class TestLockServer : LockFacade {

    override fun createLock(denomination: String, amount: BigDecimal, otherParty: String, notaryKeys: String,
                            draft: String): String = "not-a-random-string"

    override fun unlock(reservationRef: String, proof: DigitalSignatureAndMetadata, key: ByteBuffer): String = "confirmation"

}