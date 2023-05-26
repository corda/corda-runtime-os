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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
}