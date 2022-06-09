package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
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
    private var ready: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        ready = context.arguments()[2] as CompletableFuture<Unit>
    }
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val alice = MemberInfoEntry(
        HoldingIdentity(
            "Alice",
            "GROUP-1"
        ),
        "alice_pem",
        "alice.com",
    )
    private val bob = MemberInfoEntry(
        HoldingIdentity(
            "Bob",
            "GROUP-2"
        ),
        "bob_pem",
        "bob.net"
    )
    private val carol = MemberInfoEntry(
        HoldingIdentity(
            "Carol",
            "GROUP-3"
        ),
        "carol_pem",
        "carol.org"
    )
    private val aliceHash = byteArrayOf(1)
    private val bobHash = byteArrayOf(20, 21)
    private val carolHash = byteArrayOf(30, 32)
    private val alicePublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
        on { encoded } doReturn alice.sessionPublicKey.toByteArray()
    }
    private val bobPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
        on { encoded } doReturn bob.sessionPublicKey.toByteArray()
    }
    private val carolPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
        on { encoded } doReturn carol.sessionPublicKey.toByteArray()
    }
    private val keyReader = mockConstruction(PublicKeyReader::class.java) { mock, _ ->
        whenever(mock.loadPublicKey(bob.sessionPublicKey)).doReturn(bobPublicKey)
        whenever(mock.loadPublicKey(alice.sessionPublicKey)).doReturn(alicePublicKey)
        whenever(mock.loadPublicKey(carol.sessionPublicKey)).doReturn(carolPublicKey)
    }
    private val keyHasher = mockConstruction(KeyHasher::class.java) { mock, _ ->
        whenever(mock.hash(alicePublicKey)).doReturn(aliceHash)
        whenever(mock.hash(bobPublicKey)).doReturn(bobHash)
        whenever(mock.hash(carolPublicKey)).doReturn(byteArrayOf(30, 32))
    }

    private val members = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        blockingDominoTile.close()
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
            it.assertThat(members.getMemberInfo(alice.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    alice.holdingIdentity,
                    alicePublicKey,
                    KeyAlgorithm.ECDSA,
                    alice.address,
                )
            )
            it.assertThat(members.getMemberInfo(bob.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity)).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    alice.holdingIdentity,
                    alicePublicKey,
                    KeyAlgorithm.ECDSA,
                    alice.address,
                )
            )
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
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
            it.assertThat(members.getMemberInfo(alice.holdingIdentity)).isNull()
            it.assertThat(members.getMemberInfo(bob.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity)).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
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
            it.assertThat(members.getMemberInfo(alice.holdingIdentity)).isNull()
            it.assertThat(members.getMemberInfo(bob.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity)).isNull()
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
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
            it.assertThat(members.getMemberInfo(alice.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    alice.holdingIdentity,
                    alicePublicKey,
                    KeyAlgorithm.ECDSA,
                    alice.address,
                )
            )
            it.assertThat(members.getMemberInfo(bob.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
                )
            )
            it.assertThat(members.getMemberInfo(carol.holdingIdentity)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    carol.holdingIdentity,
                    carolPublicKey,
                    KeyAlgorithm.RSA,
                    carol.address,
                )
            )
            it.assertThat(members.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    alice.holdingIdentity,
                    alicePublicKey,
                    KeyAlgorithm.ECDSA,
                    alice.address,
                )
            )
            it.assertThat(members.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    bob.holdingIdentity,
                    bobPublicKey,
                    KeyAlgorithm.RSA,
                    bob.address,
                )
            )
            it.assertThat(members.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isEqualTo(
                LinkManagerMembershipGroupReader.MemberInfo(
                    carol.holdingIdentity,
                    carolPublicKey,
                    KeyAlgorithm.RSA,
                    carol.address,
                )
            )
        }
    }
}
