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
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.stub.crypto.processor.KeyDeserialiser
import net.corda.schema.TestSchema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.MessageDigest
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
        ByteBuffer.wrap("alice".toByteArray()),
        KeyAlgorithm.ECDSA,
        "alice.com",
        null,
    )
    private val bob = MemberInfoEntry(
        HoldingIdentity(
            "Bob",
            "GROUP-2"
        ),
        ByteBuffer.wrap("bob".toByteArray()),
        KeyAlgorithm.RSA,
        "bob.net",
        null,
    )
    private val carol = MemberInfoEntry(
        HoldingIdentity(
            "Carol",
            "GROUP-3"
        ),
        ByteBuffer.wrap("carol".toByteArray()),
        KeyAlgorithm.RSA,
        "carol.org",
        null,
    )
    private val aliceHash by lazy {
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())
        messageDigest.update(alice.publicKey)
        messageDigest.digest()
    }
    private val bobHash by lazy {
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())
        messageDigest.update(bob.publicKey)
        messageDigest.digest()
    }
    private val carolHash by lazy {
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())
        messageDigest.update(carol.publicKey)
        messageDigest.digest()
    }
    private val alicePublicKey = mock<PublicKey>()
    private val bobPublicKey = mock<PublicKey>()
    private val carolPublicKey = mock<PublicKey>()
    private val keyDeserialiser = mockConstruction(KeyDeserialiser::class.java) { mock, _ ->
        whenever(mock.toPublicKey("bob".toByteArray(), KeyAlgorithm.RSA)).doReturn(bobPublicKey)
        whenever(mock.toPublicKey("alice".toByteArray(), KeyAlgorithm.ECDSA)).doReturn(alicePublicKey)
        whenever(mock.toPublicKey("carol".toByteArray(), KeyAlgorithm.RSA)).doReturn(carolPublicKey)
    }

    private val members = StubMembershipGroupReader(
        lifecycleCoordinatorFactory, subscriptionFactory, instanceId, configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        keyDeserialiser.close()
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
