package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.test.MemberInfoEntry
import net.corda.schema.TestSchema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.concurrent.CompletableFuture

class StubMembershipGroupReaderTest {
    private val processor = argumentCaptor<CompactedProcessor<String, MemberInfoEntry>>()
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
    private val alice = MemberInfoEntry(
        HoldingIdentity(
            "Alice",
            "GROUP-1"
        ),
        "alice.pem",
        "alice.com",
    )
    private val bob = MemberInfoEntry(
        HoldingIdentity(
            "Bob",
            "GROUP-2"
        ),
        "bob.pem",
        "bob.net"
    )
    private val carol = MemberInfoEntry(
        HoldingIdentity(
            "Carol",
            "GROUP-3"
        ),
        "carol.pem",
        "carol.org"
    )
    private val aliceHash = byteArrayOf(1)
    private val bobHash = byteArrayOf(20, 21)
    private val carolHash = byteArrayOf(30, 32)
    private val alicePublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
        on { encoded } doReturn alice.publicKey.toByteArray()
    }
    private val bobPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
        on { encoded } doReturn bob.publicKey.toByteArray()
    }
    private val carolPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
        on { encoded } doReturn carol.publicKey.toByteArray()
    }
    private val keyReader = mockConstruction(PublicKeyReader::class.java) { mock, _ ->
        whenever(mock.loadPublicKey(bob.publicKey)).doReturn(bobPublicKey)
        whenever(mock.loadPublicKey(alice.publicKey)).doReturn(alicePublicKey)
        whenever(mock.loadPublicKey(carol.publicKey)).doReturn(carolPublicKey)
    }
    private val keyHasher = mockConstruction(KeyHasher::class.java) { mock, _ ->
        whenever(mock.hash(alicePublicKey)).doReturn(aliceHash)
        whenever(mock.hash(bobPublicKey)).doReturn(bobHash)
        whenever(mock.hash(carolPublicKey)).doReturn(byteArrayOf(30, 32))
    }

    private val members = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        keyReader.close()
        keyHasher.close()
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
    fun `onSnapshots keeps members`() {
        val membersToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(membersToPublish)

        assertSoftly {
            it.assertThat(members.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerInternalTypes.EndPoint(alice.address),
                )
            )
            it.assertThat(members.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerInternalTypes.EndPoint(alice.address),
                )
            )
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }

    @Test
    fun `onSnapshots remove old identities`() {
        val membersToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(membersToPublish)

        processor.firstValue.onSnapshot(
            mapOf(
                "bob" to bob
            )
        )

        assertSoftly {
            it.assertThat(members.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(members.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }

    @Test
    fun `onNext remove old member`() {
        val membersToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(membersToPublish)

        processor.firstValue.onNext(
            Record(
                TestSchema.MEMBER_INFO_TOPIC,
                alice.holdingIdentity.x500Name,
                null
            ),
            alice,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(members.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(members.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }
    @Test
    fun `onNext adds new member`() {
        val membersToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(membersToPublish)

        processor.firstValue.onNext(
            Record(
                TestSchema.MEMBER_INFO_TOPIC,
                alice.holdingIdentity.x500Name,
                carol
            ),
            null,
            emptyMap()
        )

        assertSoftly {
            it.assertThat(members.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerInternalTypes.EndPoint(alice.address),
                )
            )
            it.assertThat(members.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    carol.holdingIdentity.toHoldingIdentity(),
                    carolPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(carol.address),
                )
            )
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerInternalTypes.EndPoint(alice.address),
                )
            )
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(bob.address),
                )
            )
            it.assertThat(members.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isEqualTo(
                LinkManagerInternalTypes.MemberInfo(
                    carol.holdingIdentity.toHoldingIdentity(),
                    carolPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerInternalTypes.EndPoint(carol.address),
                )
            )
        }
    }
}
