package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.crypto.core.ShortHash
import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GroupPolicyListenerTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val published = argumentCaptor<List<Record<String, Any>>>()
    private val publisher = mock<PublisherWithDominoLogic> {
        on { dominoTile } doReturn mock()
        on { publish(published.capture()) } doReturn mock()
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider>()
    private val p2pParameters = mock<GroupPolicy.P2PParameters>()
    private val groupPolicy = mock<GroupPolicy> {
        on { p2pParameters } doReturn p2pParameters
    }
    private val holdingIdentity = mock<HoldingIdentity> {
        on { shortHash } doReturn ShortHash.of("1234567890ab")
    }
    private val listener = GroupPolicyListener(
        publisher,
        lifecycleCoordinatorFactory,
        groupPolicyProvider,
    )

    @Test
    fun `startListen will start listen to group policy changes`() {
        listener.startListen()

        verify(groupPolicyProvider).registerListener(any(), any())
    }

    @Test
    fun `groupAdded with null certificate will publish null value`() {
        whenever(p2pParameters.mgmClientCertificateSubject) doReturn null

        listener.groupAdded(holdingIdentity, groupPolicy)

        val publishedRecords = published.allValues.flatten().map {
            it.value as? ClientCertificateSubjects
        }.map {
            it?.subject
        }
        assertThat(publishedRecords).containsOnlyNulls()
    }

    @Test
    fun `groupAdded with valid certificate subject will publish the subject`() {
        val subject = MemberX500Name.parse("CN=Group policy client, O=Bob Corp, L=LDN, C=GB")
        whenever(p2pParameters.mgmClientCertificateSubject)
            .doReturn(subject)

        listener.groupAdded(holdingIdentity, groupPolicy)

        val publishedRecords = published.allValues.flatten().map {
            it.value as? ClientCertificateSubjects
        }.map {
            it?.subject
        }
        assertThat(publishedRecords).containsExactly(subject.toString())
    }
}
