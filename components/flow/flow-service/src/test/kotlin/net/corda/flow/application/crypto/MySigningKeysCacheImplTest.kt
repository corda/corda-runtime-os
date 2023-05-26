package net.corda.flow.application.crypto

import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.crypto.CompositeKey
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MySigningKeysCacheImplTest {

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val mySigningKeysCache = MySigningKeysCacheImpl(currentSandboxGroupContext)

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.sandboxGroupType).thenReturn(SandboxGroupType.FLOW)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `find my signing keys returns requested signing keys to owned signing keys`() {
        val key1 = mock<PublicKey>()
        val key2 = mock<PublicKey>()
        whenever(
            externalEventExecutor.execute(
                FilterMyKeysExternalEventFactory::class.java,
                setOf(key1, key2)
            )
        ).thenReturn(listOf(key1))

        assertEquals(mapOf(key1 to key1, key2 to null),mySigningKeysCache.get(setOf(key1, key2)))
    }

    @Test
    fun `find my signing keys returns requested signing keys to owned signing keys for both plain and composite keys`() {
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
            mySigningKeysCache.get(setOf(plainKey, compositeKey))
        )
    }

    @Test
    fun `find my signing keys only makes use of the firstly found composite key leaf and ignores the rest found leaves`() {
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

        assertEquals(
            mapOf(compositeKey to compositeKeyLeaf1),
            mySigningKeysCache.get(setOf(compositeKey))
        )
    }
}