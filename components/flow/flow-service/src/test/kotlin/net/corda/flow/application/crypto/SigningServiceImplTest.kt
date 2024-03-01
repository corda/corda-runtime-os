package net.corda.flow.application.crypto

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.crypto.CompositeKey
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class SigningServiceImplTest {

    private val keyEncodingService = mock<KeyEncodingService>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val mySigningKeysCache = mock<MySigningKeysCache>()
    private val captor = argumentCaptor<SignParameters>()
    private val signingService = SigningServiceImpl(
        currentSandboxGroupContext,
        externalEventExecutor,
        keyEncodingService,
        mySigningKeysCache
    )

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `sign returns the signature returned from the flow resuming`() {
        val signingKey = mock<PublicKey>().also {
            whenever(it.encoded).thenReturn(byteArrayOf(0x00))
        }
        val signature = DigitalSignatureWithKey(signingKey, byteArrayOf(1))
        val publicKey = mock<PublicKey>()
        val encodedPublicKeyBytes = byteArrayOf(2)
        whenever(keyEncodingService.encodeAsByteArray(publicKey)).thenReturn(encodedPublicKeyBytes)
        whenever(externalEventExecutor.execute(eq(CreateSignatureExternalEventFactory::class.java), captor.capture()))
            .thenReturn(signature)
        val signatureWithKeyId = DigitalSignatureWithKeyId(signature.by.fullIdHash(), signature.bytes)
        assertEquals(signatureWithKeyId, signingService.sign(byteArrayOf(1), publicKey, mock()))
        assertEquals(encodedPublicKeyBytes, captor.firstValue.encodedPublicKeyBytes)
    }

    @Test
    fun `find my signing keys returns requested signing keys to owned signing keys`() {
        val key1 = mock<PublicKey>()
        val key2 = mock<PublicKey>()
        whenever(mySigningKeysCache.get(any())).thenReturn(emptyMap())
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(key1, key2)
            )
        ).thenReturn(listOf(key1))

        assertEquals(mapOf(key1 to key1, key2 to null), signingService.findMySigningKeys(setOf(key1, key2)))
        verify(mySigningKeysCache).putAll(mapOf(key1 to key1, key2 to null))
    }

    @Test
    fun `find my signing keys returns requested signing keys to owned signing keys for both plain and composite keys`() {
        val plainKey = mock<PublicKey>()
        val compositeKeyLeaf1 = mock<PublicKey>()
        val compositeKeyLeaf2 = mock<PublicKey>()
        val compositeKey = mock<CompositeKey>()
        whenever(mySigningKeysCache.get(any())).thenReturn(emptyMap())
        whenever(compositeKey.leafKeys).thenReturn(setOf(compositeKeyLeaf1, compositeKeyLeaf2))
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(plainKey, compositeKeyLeaf1, compositeKeyLeaf2)
            )
        ).thenReturn(listOf(plainKey, compositeKeyLeaf1))

        assertEquals(
            mapOf(plainKey to plainKey, compositeKey to compositeKeyLeaf1),
            signingService.findMySigningKeys(setOf(plainKey, compositeKey))
        )
        verify(mySigningKeysCache).putAll(mapOf(plainKey to plainKey))
        verify(mySigningKeysCache).putAll(mapOf(compositeKey to compositeKeyLeaf1))
    }

    @Test
    fun `find my signing keys only makes use of the firstly found composite key leaf and ignores the rest found leaves`() {
        val compositeKeyLeaf1 = mock<PublicKey>()
        val compositeKeyLeaf2 = mock<PublicKey>()
        val compositeKey = mock<CompositeKey>()
        whenever(mySigningKeysCache.get(any())).thenReturn(emptyMap())
        whenever(compositeKey.leafKeys).thenReturn(setOf(compositeKeyLeaf1, compositeKeyLeaf2))
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(compositeKeyLeaf1, compositeKeyLeaf2)
            )
        ).thenReturn(listOf(compositeKeyLeaf1, compositeKeyLeaf2))

        assertEquals(
            mapOf(compositeKey to compositeKeyLeaf1),
            signingService.findMySigningKeys(setOf(compositeKey))
        )
        verify(mySigningKeysCache).putAll(mapOf(compositeKey to compositeKeyLeaf1))
    }

    @Test
    fun `find my signing keys returns early when all keys are cached`() {
        val key1 = mock<PublicKey>()
        val key2 = mock<PublicKey>()
        whenever(mySigningKeysCache.get(setOf(key1, key2))).thenReturn(mapOf(key1 to key1, key2 to null))
        verify(externalEventExecutor, never()).execute(eq(FilterMyKeysExternalEventFactory::class.java), any())
        assertEquals(mapOf(key1 to key1, key2 to null), signingService.findMySigningKeys(setOf(key1, key2)))
        verify(mySigningKeysCache, never()).putAll(any())
    }

    @Test
    fun `find my signing keys doesn't include cached keys in external event`() {
        val key1 = mock<PublicKey>()
        val key2 = mock<PublicKey>()
        val key3 = mock<PublicKey>()
        whenever(mySigningKeysCache.get(setOf(key1, key2, key3))).thenReturn(mapOf(key1 to key1))
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(key2, key3)
            )
        ).thenReturn(listOf(key2))
        assertEquals(mapOf(key1 to key1, key2 to key2, key3 to null), signingService.findMySigningKeys(setOf(key1, key2, key3)))
        verify(mySigningKeysCache).putAll(mapOf(key2 to key2, key3 to null))
    }

    @Test
    fun `encoding and decoding public keys is done via correct service methods`() {
        val pubKey = mock<PublicKey>()
        val keyAsString = "0x0000001"
        val keyAsBytes = byteArrayOf(100)
        whenever(keyEncodingService.decodePublicKey(keyAsString)).thenReturn(pubKey)
        whenever(keyEncodingService.decodePublicKey(keyAsBytes)).thenReturn(pubKey)
        whenever(keyEncodingService.encodeAsString(pubKey)).thenReturn(keyAsString)
        whenever(keyEncodingService.encodeAsByteArray(pubKey)).thenReturn(keyAsBytes)

        assertEquals(pubKey, signingService.decodePublicKey(keyAsString))
        assertEquals(pubKey, signingService.decodePublicKey(keyAsBytes))
        assertEquals(keyAsString, signingService.encodeAsString(pubKey))
        assert(keyAsBytes.contentEquals(signingService.encodeAsByteArray(pubKey)))
    }
}