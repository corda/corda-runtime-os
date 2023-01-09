package net.corda.p2p.linkmanager.grouppolicy

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.linkmanager.grouppolicy.StubGroupPolicyProvider.Companion.toGroupInfo
import net.corda.data.p2p.test.GroupPolicyEntry
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class StubGroupPolicyProviderTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GroupPolicyEntry>>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), eq(configuration)) } doReturn mock()
    }
    private var ready: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        ready = context.arguments()[2] as CompletableFuture<Unit>
    }
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, GroupPolicyEntry>)).invoke()
    }
    private val groupOne = GroupPolicyEntry(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "Group-1"),
        NetworkType.CORDA_5,
        listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
        listOf("cert1.1", "cert1.2")
    )
    private val groupTwo = GroupPolicyEntry(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "Group-2"),
        NetworkType.CORDA_4,
        listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY),
        listOf("cert2")
    )
    private val groupThree = GroupPolicyEntry(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "Group-3"),
        NetworkType.CORDA_5,
        listOf(ProtocolMode.AUTHENTICATION_ONLY),
        listOf("cert3")
    )

    private val groups = StubGroupPolicyProvider(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        blockingDominoTile.close()
    }

    @Test
    fun `ready is not completed before onSnapshots`() {
        assertThat(ready).isNotCompleted
    }

    @Test
    fun `ready is completed after onSnapshots`() {
        processor.firstValue.onSnapshot(emptyMap())

        assertThat(ready).isCompleted
    }

    @Test
    fun `onSnapshots keeps groups`() {
        val groupsToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                "${it.holdingIdentity.x500Name}-${it.holdingIdentity.groupId}"
            }
        processor.firstValue.onSnapshot(groupsToPublish)

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.holdingIdentity.toCorda())).isEqualTo(
                groupOne.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupTwo.holdingIdentity.toCorda())).isEqualTo(
                groupTwo.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupThree.holdingIdentity.toCorda())).isNull()
        }
    }

    @Test
    fun `onSnapshots remove old groups`() {
        val groupsToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                "${it.holdingIdentity.x500Name}-${it.holdingIdentity.groupId}"
            }
        processor.firstValue.onSnapshot(groupsToPublish)

        processor.firstValue.onSnapshot(
            mapOf(
                "group2" to groupTwo
            )
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.holdingIdentity.toCorda())).isNull()
            it.assertThat(groups.getGroupInfo(groupTwo.holdingIdentity.toCorda())).isEqualTo(
                groupTwo.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupThree.holdingIdentity.toCorda())).isNull()
        }
    }

    @Test
    fun `onNext remove old group`() {
        val groupsToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                "${it.holdingIdentity.x500Name}-${it.holdingIdentity.groupId}"
            }
        processor.firstValue.onSnapshot(groupsToPublish)

        processor.firstValue.onNext(
            Record(
                GROUP_POLICIES_TOPIC,
                "group1",
                null
            ),
            groupOne,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.holdingIdentity.toCorda())).isNull()
            it.assertThat(groups.getGroupInfo(groupTwo.holdingIdentity.toCorda())).isEqualTo(
                groupTwo.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupThree.holdingIdentity.toCorda())).isNull()
        }
    }

    @Test
    fun `onNext adds new group`() {
        val groupsToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                "${it.holdingIdentity.x500Name}-${it.holdingIdentity.groupId}"
            }
        processor.firstValue.onSnapshot(groupsToPublish)

        processor.firstValue.onNext(
            Record(
                GROUP_POLICIES_TOPIC,
                "g",
                groupThree
            ),
            null,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.holdingIdentity.toCorda())).isEqualTo(
                groupOne.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupTwo.holdingIdentity.toCorda())).isEqualTo(
                groupTwo.toGroupInfo()
            )
            it.assertThat(groups.getGroupInfo(groupThree.holdingIdentity.toCorda())).isEqualTo(
                groupThree.toGroupInfo()
            )
        }
    }

    @Test
    fun `register listener will notify on new group`() {
        val listener = mock<GroupPolicyListener>()

        groups.registerListener(listener)

        processor.firstValue.onNext(
            Record(
                GROUP_POLICIES_TOPIC,
                "g",
                groupThree,
            ),
            null,
            emptyMap()
        )

        verify(listener).groupAdded(groupThree.toGroupInfo())
    }
}
