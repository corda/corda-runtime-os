package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.schema.TestSchema.Companion.NETWORK_MAP_TOPIC
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.NetworkMapEntry
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

class StubNetworkMapTest {

    private var clientProcessor: CompactedProcessor<String, NetworkMapEntry>? = null

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), any<CompactedProcessor<String, KeyPairEntry>>(), any()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            clientProcessor = invocation.arguments[1] as CompactedProcessor<String, NetworkMapEntry>
            mock()
        }
    }

    private val resourcesHolder = mock<ResourcesHolder>()
    private lateinit var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
    private val dominoTile = Mockito.mockConstruction(DominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((ResourcesHolder) -> CompletableFuture<Unit>)
        whenever(mock.isRunning).doReturn(true)
    }

    private val networkMap = StubNetworkMap(mock(), subscriptionFactory, 1, SmartConfigImpl.empty())

    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())
    private val rsaKeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    private val ecdsaKeyPairGenerator = KeyPairGenerator.getInstance("EC")

    private val groupId1 = "group-1"
    private val groupId2 = "group-2"

    private val aliceName = "O=Alice, L=London, C=GB"
    private val aliceKeyPair = rsaKeyPairGenerator.genKeyPair()
    private val aliceAddress = "http://alice.com"

    private val bobName = "O=Bob, L=London, C=GB"
    private val bobKeyPair = rsaKeyPairGenerator.genKeyPair()
    private val bobAddress = "http://bob.com"

    private val charlieName = "O=Charlie, L=London, C=GB"
    private val charlieKeyPair = ecdsaKeyPairGenerator.genKeyPair()
    private val charlieAddress = "http://charlie.com"

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        resourcesHolder.close()
    }

    @Test
    fun `network map maintains a valid dataset of entries and responds successfully to lookups`() {
        val snapshot = mapOf(
            "$aliceName-$groupId1" to NetworkMapEntry(
                HoldingIdentity(aliceName, groupId1),
                ByteBuffer.wrap(aliceKeyPair.public.encoded),
                KeyAlgorithm.RSA, aliceAddress,
                NetworkType.CORDA_4
            ),
            "$bobName-$groupId1" to NetworkMapEntry(
                HoldingIdentity(bobName, groupId1),
                ByteBuffer.wrap(bobKeyPair.public.encoded),
                KeyAlgorithm.RSA, bobAddress,
                NetworkType.CORDA_4
            ),
        )
        val charlieEntry = "$charlieName-$groupId2" to NetworkMapEntry(
            HoldingIdentity(charlieName, groupId2),
            ByteBuffer.wrap(charlieKeyPair.public.encoded),
            KeyAlgorithm.ECDSA, charlieAddress,
            NetworkType.CORDA_5
        )
        createResources(resourcesHolder)
        clientProcessor!!.onSnapshot(snapshot)
        clientProcessor!!.onNext(Record(NETWORK_MAP_TOPIC, charlieEntry.first, charlieEntry.second), null, snapshot + charlieEntry)

        assertThat(networkMap.getNetworkType(groupId1)).isEqualTo(LinkManagerNetworkMap.NetworkType.CORDA_4)
        assertThat(networkMap.getNetworkType(groupId2)).isEqualTo(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val aliceMemberInfoByIdentity = networkMap.getMemberInfo(LinkManagerNetworkMap.HoldingIdentity(aliceName, groupId1))
        assertThat(aliceMemberInfoByIdentity!!.publicKey).isEqualTo(aliceKeyPair.public)
        assertThat(aliceMemberInfoByIdentity.endPoint.address).isEqualTo(aliceAddress)
        assertThat(aliceMemberInfoByIdentity.publicKeyAlgorithm).isEqualTo(net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA)

        val aliceMemberInfoByKeyHash = networkMap.getMemberInfo(calculateHash(aliceKeyPair.public.encoded), groupId1)
        assertThat(aliceMemberInfoByKeyHash).isEqualTo(aliceMemberInfoByIdentity)

        assertThat(networkMap.getMemberInfo(LinkManagerNetworkMap.HoldingIdentity(bobName, groupId1))).isNotNull
        assertThat(networkMap.getMemberInfo(LinkManagerNetworkMap.HoldingIdentity(charlieName, groupId2))).isNotNull

        clientProcessor!!.onNext(Record(NETWORK_MAP_TOPIC, charlieEntry.first, null), charlieEntry.second, snapshot)

        assertThat(networkMap.getMemberInfo(LinkManagerNetworkMap.HoldingIdentity(charlieName, groupId1))).isNull()
    }

//TODOs : this will be refactored as part of CORE-3147
//    @Test
//    fun `create resource starts the subscription and adds it to the resource tracker`() {
//        createResources(resourcesHolder)
//        val capture = argumentCaptor<AutoCloseable>()
//        verify(resourcesHolder).keep(capture.capture())
//        verify(capture.lastValue as CompactedSubscription<*, *>).start()
//    }

    @Test
    fun `onSnapshot completes the resource future`() {
        val future = createResources(resourcesHolder)
        clientProcessor!!.onSnapshot(emptyMap())
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    private fun calculateHash(publicKey: ByteArray): ByteArray {
        messageDigest.reset()
        messageDigest.update(publicKey)
        return messageDigest.digest()
    }

}
