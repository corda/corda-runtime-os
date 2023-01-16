package net.corda.flow.application.crypto

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.crypto.external.events.CreateSignatureExternalEventFactory
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.application.crypto.external.events.SignParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class SigningServiceImplTest {

    private val keyEncodingService = mock<KeyEncodingService>()
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val captor = argumentCaptor<SignParameters>()
    private val signingService = SigningServiceImpl(externalEventExecutor, keyEncodingService)

    @Test
    fun `sign returns the signature returned from the flow resuming`() {
        val signature = DigitalSignature.WithKey(mock(), byteArrayOf(1), emptyMap())
        val publicKey = mock<PublicKey>()
        val encodedPublicKeyBytes = byteArrayOf(2)
        whenever(keyEncodingService.encodeAsByteArray(publicKey)).thenReturn(encodedPublicKeyBytes)
        whenever(externalEventExecutor.execute(eq(CreateSignatureExternalEventFactory::class.java), captor.capture()))
            .thenReturn(signature)
        assertEquals(signature, signingService.sign(byteArrayOf(1), publicKey, mock()))
        assertEquals(encodedPublicKeyBytes, captor.firstValue.encodedPublicKeyBytes)
    }

    @Test
    fun `get my signing keys returns requested signing keys to owned signing keys`() {
        val key1 = mock<PublicKey>()
        val key2 = mock<PublicKey>()
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(key1, key2)
            )
        ).thenReturn(listOf(key1))

        assertEquals(mapOf(key1 to key1, key2 to null), signingService.getMySigningKeys(setOf(key1, key2)))
    }

    @Test
    fun `get my signing keys returns requested signing keys to owned signing keys for both plain and composite keys`() {
        val plainKey = mock<PublicKey>()
        val compositeKeyLeaf1 = mock<PublicKey>()
        val compositeKeyLeaf2 = mock<PublicKey>()
        val compositeKey = mock<CompositeKey>()
        whenever(compositeKey.leafKeys).thenReturn(setOf(compositeKeyLeaf1, compositeKeyLeaf2))
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(plainKey, compositeKeyLeaf1, compositeKeyLeaf2)
            )
        ).thenReturn(listOf(plainKey, compositeKeyLeaf1))

        assertEquals(
            mapOf(plainKey to plainKey, compositeKey to compositeKeyLeaf1),
            signingService.getMySigningKeys(setOf(plainKey, compositeKey))
        )
    }

    @Test
    fun `get my signing keys throws if more than one leaves are found to be owned for the same composite key`() {
        val compositeKeyLeaf1 = mock<PublicKey>()
        val compositeKeyLeaf2 = mock<PublicKey>()
        val compositeKey = mock<CompositeKey>()
        whenever(compositeKey.leafKeys).thenReturn(setOf(compositeKeyLeaf1, compositeKeyLeaf2))
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(compositeKeyLeaf1, compositeKeyLeaf2)
            )
        ).thenReturn(listOf(compositeKeyLeaf1, compositeKeyLeaf2))

        assertThrows<IllegalStateException>(
            "A node should be owning one key at most per composite key, but two owned keys were found for composite key: \"$compositeKey\" " +
                    " first: \"$compositeKeyLeaf1\" second: \"$compositeKeyLeaf2\""
        ) {
            signingService.getMySigningKeys(setOf(compositeKey))
        }
    }
}