package net.corda.flow.application.crypto

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
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
        val CPK1_CHECKSUM = SecureHashImpl("ALG", byteArrayOf(0, 0, 0, 0))
    }

    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val aliceVirtualNodeContext = VirtualNodeContext(
        ALICE_X500_HOLDING_IDENTITY.toCorda(),
        setOf(CPK1_CHECKSUM),
        FLOW,
        null
    )
    private val bobVirtualNodeContext = VirtualNodeContext(
        BOB_X500_HOLDING_IDENTITY.toCorda(),
        setOf(CPK1_CHECKSUM),
        FLOW,
        null
    )

    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val cacheEviction = mock<CacheEviction>()
    private val mySigningKeysCache = MySigningKeysCacheImpl(currentSandboxGroupContext, cacheEviction)

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.sandboxGroupType).thenReturn(FLOW)
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
    fun `removes keys by virtual node context`() {
        // return vnode in this order in consecutive calls of the function (alice, bob, alice, bob)
        whenever(sandbox.virtualNodeContext).thenReturn(
            aliceVirtualNodeContext,
            bobVirtualNodeContext,
            aliceVirtualNodeContext,
            bobVirtualNodeContext,
        )
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)

        // alice put cache
        mySigningKeysCache.putAll(mapOf(KEY_A to KEY_A, KEY_B to null))
        // bob put cache
        mySigningKeysCache.putAll(mapOf(KEY_C to KEY_C, KEY_D to null))
        mySigningKeysCache.remove(aliceVirtualNodeContext)

        // alice's cache should be empty
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).containsExactlyInAnyOrderEntriesOf(
            emptyMap()
        )

        // there should bob's cache only
        assertThat(mySigningKeysCache.get(setOf(KEY_A, KEY_B, KEY_C, KEY_D))).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                KEY_C to KEY_C,
                KEY_D to null
            )
        )
    }
}