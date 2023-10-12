package net.corda.ledger.utxo.flow.impl.cache.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.data.identity.HoldingIdentity
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StateAndRefCacheImplTest {

    private companion object {
        val STATE_REF_1 = StateRef(mock(), 0)
        val STATE_REF_2 = StateRef(mock(), 0)
        val STATE_REF_3 = StateRef(mock(), 0)
        val STATE_REF_4 = StateRef(mock(), 0)

        val STATE_AND_REF_1 = mock<StateAndRef<ContractState>>()
        val STATE_AND_REF_2 = mock<StateAndRef<ContractState>>()
        val STATE_AND_REF_3 = mock<StateAndRef<ContractState>>()
        val STATE_AND_REF_4 = mock<StateAndRef<ContractState>>()

        val ALICE_X500_HOLDING_IDENTITY = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group1")
        val BOB_X500_HOLDING_IDENTITY = HoldingIdentity("CN=Bob, O=Alice Corp, L=LDN, C=GB", "group1")
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
    private val stateAndRefCache = StateAndRefCacheImpl(currentSandboxGroupContext, cacheEviction)

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.sandboxGroupType).thenReturn(FLOW)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)

        whenever(STATE_AND_REF_1.ref).thenReturn(STATE_REF_1)
        whenever(STATE_AND_REF_2.ref).thenReturn(STATE_REF_2)
        whenever(STATE_AND_REF_3.ref).thenReturn(STATE_REF_3)
        whenever(STATE_AND_REF_4.ref).thenReturn(STATE_REF_4)
    }

    @Test
    fun `put entry and get entry from the cache`() {
        stateAndRefCache.putAll(listOf(STATE_AND_REF_1))

        val result = stateAndRefCache.get(setOf(STATE_REF_1))

        assertThat(result).hasSize(1)
        assertThat(result.values.first()).isEqualTo(STATE_AND_REF_1)
    }

    @Test
    fun `put entries in bulk and get entries one by one from the cache`() {
        stateAndRefCache.putAll(listOf(STATE_AND_REF_1, STATE_AND_REF_2))

        val result = stateAndRefCache.get(setOf(STATE_REF_1))

        assertThat(result).hasSize(1)
        assertThat(result.values).containsExactly(STATE_AND_REF_1)

        val result2 = stateAndRefCache.get(setOf(STATE_REF_2))

        assertThat(result2).hasSize(1)
        assertThat(result2.values).containsExactly(STATE_AND_REF_2)
    }

    @Test
    fun `put entries in bulk and get entries in bulk from the cache`() {
        stateAndRefCache.putAll(listOf(STATE_AND_REF_1, STATE_AND_REF_2))

        val result = stateAndRefCache.get(setOf(STATE_REF_1, STATE_REF_2))

        assertThat(result).hasSize(2)
        assertThat(result.values).containsExactly(STATE_AND_REF_1, STATE_AND_REF_2)
    }

    @Test
    fun `returns empty map when the cache is empty`() {
        assertThat(stateAndRefCache.get(setOf(STATE_REF_1))).isEmpty()
    }

    @Test
    fun `removes keys by virtual node context`() {
        // subsequent calls of the function call return in the order of alice, bob, alice, bob
        whenever(sandbox.virtualNodeContext).thenReturn(
            aliceVirtualNodeContext,
            bobVirtualNodeContext,
            aliceVirtualNodeContext,
            bobVirtualNodeContext
        )
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)

        whenever(STATE_AND_REF_1.ref).thenReturn(STATE_REF_1)
        whenever(STATE_AND_REF_2.ref).thenReturn(STATE_REF_2)

        stateAndRefCache.putAll(listOf(STATE_AND_REF_1, STATE_AND_REF_2))
        stateAndRefCache.putAll(listOf(STATE_AND_REF_3, STATE_AND_REF_4))
        stateAndRefCache.remove(aliceVirtualNodeContext)

        // alice's cache should be empty
        assertThat(
            stateAndRefCache.get(setOf(STATE_REF_1, STATE_REF_2, STATE_REF_3, STATE_REF_4)).values
        ).containsExactlyElementsOf(emptyList())
        // bob's cache should exist
        assertThat(
            stateAndRefCache.get(setOf(STATE_REF_1, STATE_REF_2, STATE_REF_3, STATE_REF_4)).values
        ).containsExactlyElementsOf(listOf(STATE_AND_REF_3, STATE_AND_REF_4))
    }
}
