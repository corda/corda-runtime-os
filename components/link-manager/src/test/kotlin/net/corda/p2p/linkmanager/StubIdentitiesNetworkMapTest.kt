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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.KeyAlgorithm
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

class StubIdentitiesNetworkMapTest {
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
        "alice.com"
    )
    private val bob = MemberInfoEntry(
        HoldingIdentity(
            "Bob",
            "GROUP-2"
        ),
        ByteBuffer.wrap("bob".toByteArray()),
        KeyAlgorithm.RSA,
        "bob.net"
    )
    private val carol = MemberInfoEntry(
        HoldingIdentity(
            "Carol",
            "GROUP-3"
        ),
        ByteBuffer.wrap("carol".toByteArray()),
        KeyAlgorithm.RSA,
        "carol.org"
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

    private val identities = StubIdentitiesNetworkMap(
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
    fun `onSnapshots keeps identities`() {
        val identitiesToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        assertSoftly {
            it.assertThat(identities.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerNetworkMap.EndPoint(alice.address),
                )
            )
            it.assertThat(identities.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(identities.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerNetworkMap.EndPoint(alice.address),
                )
            )
            it.assertThat(identities.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }

    @Test
    fun `onSnapshots remove old identities`() {
        val identitiesToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

        processor.firstValue.onSnapshot(
            mapOf(
                "bob" to bob
            )
        )

        assertSoftly {
            it.assertThat(identities.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(identities.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(identities.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(identities.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }

    @Test
    fun `onNext remove old identity`() {
        val identitiesToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

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
            it.assertThat(identities.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(identities.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isNull()
            it.assertThat(identities.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isNull()
            it.assertThat(identities.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isNull()
        }
    }
    @Test
    fun `onNext adds new identity`() {
        val identitiesToPublish = listOf(alice, bob)
            .associateBy {
                it.holdingIdentity.x500Name
            }
        processor.firstValue.onSnapshot(identitiesToPublish)

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
            it.assertThat(identities.getMemberInfo(alice.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerNetworkMap.EndPoint(alice.address),
                )
            )
            it.assertThat(identities.getMemberInfo(bob.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carol.holdingIdentity.toHoldingIdentity())).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    carol.holdingIdentity.toHoldingIdentity(),
                    carolPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(carol.address),
                )
            )
            it.assertThat(identities.getMemberInfo(aliceHash, alice.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    alice.holdingIdentity.toHoldingIdentity(),
                    alicePublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA,
                    LinkManagerNetworkMap.EndPoint(alice.address),
                )
            )
            it.assertThat(identities.getMemberInfo(bobHash, bob.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    bob.holdingIdentity.toHoldingIdentity(),
                    bobPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(bob.address),
                )
            )
            it.assertThat(identities.getMemberInfo(carolHash, carol.holdingIdentity.groupId)).isEqualTo(
                LinkManagerNetworkMap.MemberInfo(
                    carol.holdingIdentity.toHoldingIdentity(),
                    carolPublicKey,
                    net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA,
                    LinkManagerNetworkMap.EndPoint(carol.address),
                )
            )
        }
    }
}
