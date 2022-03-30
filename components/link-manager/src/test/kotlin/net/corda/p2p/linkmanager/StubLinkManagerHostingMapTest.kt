package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.test.HostedIdentityEntry
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

class StubLinkManagerHostingMapTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val processor = argumentCaptor<CompactedProcessor<String, HostedIdentityEntry>>()
    private val subscription = mock<CompactedSubscription<String, HostedIdentityEntry>>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                eq(configuration),
            )
        } doReturn subscription
    }
    private val subscriptionTile = mockConstruction(SubscriptionDominoTile::class.java)
    private var createResources: ((ResourcesHolder) -> CompletableFuture<Unit>)? = null
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as? ((ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val publicKeyOne = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(0, 1, 2)
    }
    private val entryOne = HostedIdentityEntry(
        HoldingIdentity("x500", "group"),
        "id1",
        "id2",
        listOf("cert1", "cert2"),
        "pem"
    )
    private val publicKeyReader = mockConstruction(PublicKeyReader::class.java) { mock, _ ->
        whenever(mock.loadPublicKey("pem")).thenReturn(publicKeyOne)
    }
    private val keyHasher = mockConstruction(KeyHasher::class.java) {mock, _ ->
        whenever(mock.hash(publicKeyOne)).thenReturn(byteArrayOf(5, 6, 7))
    }

    private val testObject = StubLinkManagerHostingMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        100,
        configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionTile.close()
        dominoTile.close()
        publicKeyReader.close()
        keyHasher.close()
    }

    @Test
    fun `onSnapshot adds data`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(
            testObject.isHostedLocally(entryOne.holdingIdentity.toHoldingIdentity())
        ).isTrue
    }

    @Test
    fun `onSnapshot adds only data sent`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(
            testObject.isHostedLocally(
                LinkManagerInternalTypes.HoldingIdentity(
                    "x500", "another group"
                )
            )
        ).isFalse
    }

    @Test
    fun `onSnapshot complete the future`() {
        val resourceHolder = ResourcesHolder()
        val future = createResources?.invoke(resourceHolder)
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(future).isCompleted
    }

    @Test
    fun `future will not complete before onSnapshot`() {
        val resourceHolder = ResourcesHolder()
        val future = createResources?.invoke(resourceHolder)

        assertThat(future).isNotCompleted
    }

    @Test
    fun `onNext will remove old value`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        processor.firstValue.onNext(
            Record("topic", "key", null),
            entryOne,
            emptyMap()
        )

        assertThat(
            testObject.isHostedLocally(
                LinkManagerInternalTypes.HoldingIdentity(
                    "x500", "group"
                )
            )
        ).isFalse
    }

    @Test
    fun `onNext will add new value`() {
        processor.firstValue.onNext(
            Record("topic", "key", entryOne),
            null,
            emptyMap()
        )

        assertThat(
            testObject.isHostedLocally(
                LinkManagerInternalTypes.HoldingIdentity(
                    "x500", "group"
                )
            )
        ).isTrue
    }

    @Test
    fun `getInfo return the correct data`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertSoftly {
            it.assertThat(
                testObject.getInfo(
                    byteArrayOf(5, 6, 7),
                    entryOne.holdingIdentity.groupId,
                )
            ).isEqualTo(
                HostingMapListener.IdentityInfo(
                    holdingIdentity = entryOne.holdingIdentity,
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    sessionKeyTenantId = "id2",
                    publicKey = publicKeyOne
                )
            )
            it.assertThat(testObject.getInfo(LinkManagerInternalTypes.HoldingIdentity("", ""))).isNull()
        }
    }

    @Test
    fun `getInfo by public key return the correct data`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertSoftly {
            it.assertThat(testObject.getInfo(entryOne.holdingIdentity.toHoldingIdentity())).isEqualTo(
                HostingMapListener.IdentityInfo(
                    holdingIdentity = entryOne.holdingIdentity,
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    sessionKeyTenantId = "id2",
                    publicKey = publicKeyOne
                )
            )
            it.assertThat(testObject.getInfo(LinkManagerInternalTypes.HoldingIdentity("", ""))).isNull()
        }
    }

    @Test
    fun `onSnapshot send data to listener`() {
        val entries = mutableListOf<HostingMapListener.IdentityInfo>()
        testObject.registerListener(object : HostingMapListener {
            override fun identityAdded(identityInfo: HostingMapListener.IdentityInfo) {
                entries.add(identityInfo)
            }
        })

        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(entries).containsExactly(
            HostingMapListener.IdentityInfo(
                entryOne.holdingIdentity,
                entryOne.tlsCertificates,
                entryOne.tlsTenantId,
                entryOne.identityTenantId,
                publicKeyOne,
            )
        )
    }
}
