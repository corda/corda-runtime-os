package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.test.GroupNetworkMapEntry
import net.corda.schema.TestSchema
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

class StubGroupsNetworkMapTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GroupNetworkMapEntry>>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), eq(configuration)) } doReturn mock()
    }
    private val instanceId = 321
    private lateinit var ready: CompletableFuture<Unit>
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        val createResources = context.arguments()[2] as ((ResourcesHolder) -> CompletableFuture<Unit>)
        ready = createResources.invoke(mock())
        whenever(mock.isRunning).doReturn(true)
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val groupOne = GroupNetworkMapEntry(
        "Group-1",
        NetworkType.CORDA_5,
        listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
        listOf("cert1.1", "cert1.2")
    )
    private val groupTwo = GroupNetworkMapEntry(
        "Group-2",
        NetworkType.CORDA_4,
        listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION, ProtocolMode.AUTHENTICATION_ONLY),
        listOf("cert2")
    )
    private val groupThree = GroupNetworkMapEntry(
        "Group-3",
        NetworkType.CORDA_5,
        listOf(ProtocolMode.AUTHENTICATION_ONLY),
        listOf("cert3")
    )

    private val groups = StubGroupsNetworkMap(
        lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
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
        val identitiesToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                it.groupId
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupOne.groupId,
                    groupOne.networkType,
                    groupOne.protocolModes.toSet(),
                    groupOne.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupTwo.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupTwo.groupId,
                    groupTwo.networkType,
                    groupTwo.protocolModes.toSet(),
                    groupTwo.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupThree.groupId)).isNull()
        }
    }

    @Test
    fun `onSnapshots remove old groups`() {
        val identitiesToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                it.groupId
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        processor.firstValue.onSnapshot(
            mapOf(
                "group2" to groupTwo
            )
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.groupId)).isNull()
            it.assertThat(groups.getGroupInfo(groupTwo.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupTwo.groupId,
                    groupTwo.networkType,
                    groupTwo.protocolModes.toSet(),
                    groupTwo.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupThree.groupId)).isNull()
        }
    }

    @Test
    fun `onNext remove old group`() {
        val identitiesToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                it.groupId
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        processor.firstValue.onNext(
            Record(
                TestSchema.GROUP_NETWORK_MAP_TOPIC,
                "group1",
                null
            ),
            groupOne,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.groupId)).isNull()
            it.assertThat(groups.getGroupInfo(groupTwo.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupTwo.groupId,
                    groupTwo.networkType,
                    groupTwo.protocolModes.toSet(),
                    groupTwo.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupThree.groupId)).isNull()
        }
    }

    @Test
    fun `onNext adds new identity`() {
        val identitiesToPublish = listOf(groupOne, groupTwo)
            .associateBy {
                it.groupId
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        processor.firstValue.onNext(
            Record(
                TestSchema.GROUP_NETWORK_MAP_TOPIC,
                "g",
                groupThree
            ),
            null,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(groups.getGroupInfo(groupOne.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupOne.groupId,
                    groupOne.networkType,
                    groupOne.protocolModes.toSet(),
                    groupOne.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupTwo.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupTwo.groupId,
                    groupTwo.networkType,
                    groupTwo.protocolModes.toSet(),
                    groupTwo.trustedCertificates,
                )
            )
            it.assertThat(groups.getGroupInfo(groupThree.groupId)).isEqualTo(
                NetworkMapListener.GroupInfo(
                    groupThree.groupId,
                    groupThree.networkType,
                    groupThree.protocolModes.toSet(),
                    groupThree.trustedCertificates,
                )
            )
        }
    }

    @Test
    fun `register listener will notify on new group`() {
        val listener = mock<NetworkMapListener>()

        groups.registerListener(listener)

        processor.firstValue.onNext(
            Record(
                TestSchema.GROUP_NETWORK_MAP_TOPIC,
                "g",
                groupThree,
            ),
            null,
            emptyMap()
        )

        verify(listener).groupAdded(
            NetworkMapListener.GroupInfo(
                groupThree.groupId,
                groupThree.networkType,
                groupThree.protocolModes.toSet(),
                groupThree.trustedCertificates,
            )
        )
    }
}
