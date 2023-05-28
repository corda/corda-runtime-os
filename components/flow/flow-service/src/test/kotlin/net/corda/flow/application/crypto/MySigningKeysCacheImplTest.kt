package net.corda.flow.application.crypto

import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.application.crypto.external.events.FilterMyKeysExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.crypto.CompositeKey
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MySigningKeysCacheImplTest {

    private companion object {
        val KEY_A = mock<PublicKey>()
        val KEY_B = mock<PublicKey>()
        val KEY_C = mock<PublicKey>()
        val KEY_D = mock<PublicKey>()
    }

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
    fun `put and get all entries from the cache`() {
        mySigningKeysCache.putAll(mapOf(KEY_A to KEY_A, KEY_B to null))
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                KEY_A to KEY_A,
                KEY_B to null
            )
        )
        mySigningKeysCache.putAll(mapOf(KEY_C to KEY_C, KEY_D to null))
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                KEY_A to KEY_A,
                KEY_B to null,
                KEY_C to KEY_C,
                KEY_D to null
            )
        )
    }

    @Test
    fun `returns nothing when the cache is empty`() {
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).isEmpty()
    }

    @Test
    fun `doesn't put keys if the input is empty`() {
        mySigningKeysCache.putAll(mapOf())
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).isEmpty()
    }

    @Test
    fun `removes keys by holding identity`() {
        whenever(virtualNodeContext.holdingIdentity).thenReturn(
            ALICE_X500_HOLDING_IDENTITY.toCorda(),
            ALICE_X500_HOLDING_IDENTITY.toCorda(),
            BOB_X500_HOLDING_IDENTITY.toCorda(),
            BOB_X500_HOLDING_IDENTITY.toCorda()
        )
        mySigningKeysCache.putAll(mapOf(KEY_A to KEY_A, KEY_B to null, KEY_C to KEY_C, KEY_D to null))
        mySigningKeysCache.remove(ALICE_X500_HOLDING_IDENTITY.toCorda(), SandboxGroupType.FLOW)

        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                KEY_A to KEY_A,
                KEY_B to null
            )
        )
    }
}