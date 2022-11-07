package net.corda.p2p.linkmanager

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class ForwardingGroupPolicyProviderTest {

    private val alice = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group-1")
    private val certificate = "Certificate"
    private val mockSessionTruststore = listOf(certificate)
    private val p2pParams = mock<GroupPolicy.P2PParameters>().also {
        whenever(it.tlsPki).thenReturn(P2PParameters.TlsPkiMode.STANDARD)
        whenever(it.protocolMode).thenReturn(P2PParameters.ProtocolMode.AUTH_ENCRYPT)
        whenever(it.tlsTrustRoots).thenReturn(emptyList())
        whenever(it.sessionPki).thenReturn(P2PParameters.SessionPkiMode.NO_PKI)
        whenever(it.sessionTrustRoots).thenReturn(mockSessionTruststore)
    }
    private val groupPolicy = mock<GroupPolicy>().also {
        whenever(it.groupId).thenReturn(alice.groupId)
        whenever(it.p2pParameters).thenReturn(p2pParams)
    }

    private var dependentChildren: Collection<LifecycleCoordinatorName> = mutableListOf()
    private var managedChildren: Collection<NamedLifecycle> = mutableListOf()

    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("unchecked_cast")
        dependentChildren = context.arguments()[4] as Collection<LifecycleCoordinatorName>
        @Suppress("unchecked_cast")
        managedChildren = context.arguments()[5] as Collection<NamedLifecycle>
    }

    private val stubGroupPolicyProviderCoordinatorName = LifecycleCoordinatorName("stub_group_policy")
    private val stubGroupPolicyProviderNamedLifecycle = NamedLifecycle(mock(), stubGroupPolicyProviderCoordinatorName)
    private val stubGroupPolicyProvider = mockConstruction(StubGroupPolicyProvider::class.java) { mock, _ ->
        val policyProviderTile = mock<ComplexDominoTile>()
        whenever(policyProviderTile.coordinatorName).thenReturn(stubGroupPolicyProviderCoordinatorName)
        whenever(policyProviderTile.toNamedLifecycle()).thenReturn(stubGroupPolicyProviderNamedLifecycle)
        whenever(mock.dominoTile).thenReturn(policyProviderTile)
    }
    private val mockCertificate = mock<Certificate>()
    private val certificateFactory = mock<CertificateFactory> {
        whenever(it.generateCertificate(any())).thenReturn(mockCertificate)
    }
    private val keyStore = mock<KeyStore>()
    private val keyStoreStaticMock = Mockito.mockStatic(KeyStore::class.java).also {
        it.`when`<KeyStore> {
            KeyStore.getInstance(any())
        }.doReturn(keyStore)
    }
    private val groupInfo =
        GroupPolicyListener.GroupInfo(
            alice,
            NetworkType.CORDA_5,
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
            listOf(),
            P2PParameters.SessionPkiMode.NO_PKI,
            GroupPolicyListener.KeyStoreWithPem(keyStore, listOf(""))
        )

    private val realGroupPolicyProvider = mock<GroupPolicyProvider>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()
    private val membershipQueryClient = mock<MembershipQueryClient>()

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        stubGroupPolicyProvider.close()
        keyStoreStaticMock.close()
    }

    @Test
    fun `dependent and managed children are set properly when using the real policy provider`() {
        createForwardingGroupPolicyProvider()

        assertThat(dependentChildren).hasSize(4)
        assertThat(dependentChildren).containsExactlyInAnyOrder(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
            LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
            LifecycleCoordinatorName.forComponent<MembershipQueryClient>()
        )
        assertThat(managedChildren).hasSize(4)
        assertThat(managedChildren).containsExactlyInAnyOrder(
            NamedLifecycle(realGroupPolicyProvider, LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()),
            NamedLifecycle(
                virtualNodeInfoReadService,
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
            ),
            NamedLifecycle(cpiInfoReadService, LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
            NamedLifecycle(membershipQueryClient, LifecycleCoordinatorName.forComponent<MembershipQueryClient>())
        )
    }

    @Test
    fun `get group info delegates to the real policy provider properly`() {
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicy)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice)).isEqualTo(groupInfo)
        verify(certificateFactory).generateCertificate(any())
    }

    @Test
    fun `get group info delegates to the real policy provider properly for c4 network`() {
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()
        whenever(groupPolicy.p2pParameters.tlsPki).thenReturn(P2PParameters.TlsPkiMode.CORDA_4)

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicy)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice)).isEqualTo(groupInfo.copy(networkType = NetworkType.CORDA_4))
    }

    @Test
    fun `get group info delegates to the real policy provider properly for network with authentication only mode`() {
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()
        whenever(groupPolicy.p2pParameters.protocolMode).thenReturn(P2PParameters.ProtocolMode.AUTH)

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicy)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice))
            .isEqualTo(groupInfo.copy(protocolModes = setOf(ProtocolMode.AUTHENTICATION_ONLY)))
    }

    @Test
    fun `get group info returns null if real group policy provider fails to find a group policy for the holding identity`() {
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()
        whenever(groupPolicy.p2pParameters.protocolMode).thenReturn(P2PParameters.ProtocolMode.AUTH)

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(null)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice)).isNull()
    }

    @Test
    fun `get group info returns null if CertificateException is thrown when generating session certificate from byte array `() {
        whenever(certificateFactory.generateCertificate(any())).thenThrow(CertificateException("Bad cert"))
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicy)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice)).isNull()
    }

    @Test
    fun `get group info returns null if KeyStoreException is thrown when loading session certificate`() {
        whenever(keyStore.setCertificateEntry(any(), any())).thenThrow(KeyStoreException("Could not load cert."))
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()

        whenever(realGroupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicy)
        assertThat(forwardingGroupPolicyProvider.getGroupInfo(alice)).isNull()
    }

    @Test
    fun `register listener delegates to the real policy provider properly`() {
        val forwardingGroupPolicyProvider = createForwardingGroupPolicyProvider()

        val listener = mock<GroupPolicyListener>()
        forwardingGroupPolicyProvider.registerListener(listener)
        val capturedListener = argumentCaptor<(net.corda.virtualnode.HoldingIdentity, GroupPolicy) -> Unit>()
        verify(realGroupPolicyProvider).registerListener(any(), capturedListener.capture())

        capturedListener.firstValue.invoke(alice, groupPolicy)
        verify(listener).groupAdded(groupInfo)
    }

    private fun createForwardingGroupPolicyProvider(): ForwardingGroupPolicyProvider {
        return ForwardingGroupPolicyProvider(
            mock(), realGroupPolicyProvider, virtualNodeInfoReadService, cpiInfoReadService, membershipQueryClient, certificateFactory
        )
    }

}