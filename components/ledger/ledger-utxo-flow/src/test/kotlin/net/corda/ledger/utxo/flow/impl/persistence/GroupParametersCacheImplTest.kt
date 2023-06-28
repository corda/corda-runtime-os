package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.data.identity.HoldingIdentity
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GroupParametersCacheImplTest {

    private companion object {
        val KEY_1 = mock<SecureHash>()
        val KEY_2 = mock<SecureHash>()
        val GROUP_PARAMS_1 = mock<SignedGroupParameters>()
        val GROUP_PARAMS_2 = mock<SignedGroupParameters>()

        val ALICE_X500_HOLDING_IDENTITY = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group1")
        val BOB_X500_HOLDING_IDENTITY = HoldingIdentity("CN=Bob, O=Alice Corp, L=LDN, C=GB", "group1")
    }

    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val cacheEviction = mock<CacheEviction>()
    private val groupParametersCache = GroupParametersCacheImpl(currentSandboxGroupContext, cacheEviction)

    @BeforeEach
    fun beforeEach() {
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.sandboxGroupType).thenReturn(SandboxGroupType.FLOW)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
        whenever(GROUP_PARAMS_1.hash).thenReturn(KEY_1)
        whenever(GROUP_PARAMS_2.hash).thenReturn(KEY_2)
    }

    @Test
    fun `put and get entry from the cache`() {
        groupParametersCache.put(GROUP_PARAMS_1)
        groupParametersCache.put(GROUP_PARAMS_2)
        assertThat(groupParametersCache.get(KEY_1)).isEqualTo(GROUP_PARAMS_1)
        assertThat(groupParametersCache.get(KEY_2)).isEqualTo(GROUP_PARAMS_2)
    }

    @Test
    fun `returns null when the cache is empty`() {
        assertThat(groupParametersCache.get(KEY_1)).isNull()
    }

    @Test
    fun `removes keys by holding identity`() {
        whenever(virtualNodeContext.holdingIdentity).thenReturn(
            ALICE_X500_HOLDING_IDENTITY.toCorda(),
            BOB_X500_HOLDING_IDENTITY.toCorda()
        )
        groupParametersCache.put(GROUP_PARAMS_1)
        groupParametersCache.put(GROUP_PARAMS_2)
        groupParametersCache.remove(ALICE_X500_HOLDING_IDENTITY.toCorda())

        assertThat(groupParametersCache.get(KEY_1)).isNull()
        assertThat(groupParametersCache.get(KEY_2)).isEqualTo(GROUP_PARAMS_2)
    }
}