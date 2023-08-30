package net.corda.flow.application.services

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.flow.application.services.impl.GroupParametersLookupImpl
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class GroupParametersLookupImplTest {
    private val holdingIdentity = HoldingIdentity(MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"), "group")
    private val virtualNode = mock<VirtualNodeContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    private val context = mock<SandboxGroupContext> {
        on { virtualNodeContext } doReturn virtualNode
    }
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext> {
        on { get() } doReturn context
    }
    private val parameters = mock<SignedGroupParameters>()
    private val membershipGroupReader = mock<MembershipGroupReader> {
        on { signedGroupParameters } doReturn parameters
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(holdingIdentity) } doReturn membershipGroupReader
    }
    private val publicKey = mock<PublicKey>()
    private val pem = "pem"
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(pem) } doReturn publicKey
    }
    private val mgmInfo = mock<GroupPolicy.MGMInfo> {
        on { get(PARTY_SESSION_KEYS_PEM.format(0)) } doReturn pem
    }
    private val groupPolicy = mock<GroupPolicy> {
        on { mgmInfo } doReturn mgmInfo
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(holdingIdentity) } doReturn groupPolicy
    }
    private val impl = GroupParametersLookupImpl(
        currentSandboxGroupContext,
        membershipGroupReaderProvider,
        keyEncodingService,
        groupPolicyProvider,
    )

    @Test
    fun `get return the correct parameters`() {
        assertThat(impl.getCurrentGroupParameters()).isEqualTo(parameters)
    }

    @Test
    fun `get fails when parameters are null`() {
        whenever(membershipGroupReader.signedGroupParameters).doReturn(null)

        assertThrows<IllegalArgumentException> {
            impl.getCurrentGroupParameters()
        }
    }

    @Test
    fun `getMgmKeys return the key for dynamic network`() {
        assertThat(impl.getMgmKeys()).containsExactly(publicKey)
    }

    @Test
    fun `getMgmKeys return the key for static network`() {
        whenever(mgmInfo[PARTY_SESSION_KEYS_PEM.format(0)]).doReturn(null)
        whenever(mgmInfo[PARTY_SESSION_KEYS.format(0)]).doReturn(pem)

        assertThat(impl.getMgmKeys()).containsExactly(publicKey)
    }

    @Test
    fun `getMgmKeys will fail if session key is missing`() {
        whenever(mgmInfo[PARTY_SESSION_KEYS_PEM.format(0)]).doReturn(null)

        assertThrows<IllegalArgumentException> {
            impl.getMgmKeys()
        }
    }

    @Test
    fun `getMgmKeys will fail if mgm info is missing`() {
        whenever(groupPolicy.mgmInfo).doReturn(null)

        assertThrows<IllegalArgumentException> {
            impl.getMgmKeys()
        }
    }

    @Test
    fun `getMgmKeys will fail if group policy is missing`() {
        whenever(groupPolicyProvider.getGroupPolicy(holdingIdentity)).doReturn(null)

        assertThrows<IllegalArgumentException> {
            impl.getMgmKeys()
        }
    }
}
