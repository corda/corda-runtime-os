package net.corda.p2p.linkmanager.hosting

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.HostedIdentityEntry
import net.corda.p2p.linkmanager.common.KeyHasher
import net.corda.p2p.linkmanager.common.PublicKeyReader
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
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

class LinkManagerHostingMapImplTest {
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
    private val subscriptionTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, HostedIdentityEntry>)).invoke()
    }
    private var ready: CompletableFuture<Unit>? = null
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        ready = context.arguments()[2] as CompletableFuture<Unit>
    }
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java)
    private val publicKeyOne = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(0, 1, 2)
    }
    private val bobX500Name = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
    private val entryOne = HostedIdentityEntry(
        createTestHoldingIdentity(bobX500Name, "group").toAvro(),
        "id1",
        "id2",
        listOf("cert1", "cert2"),
        "pem",
        listOf("certificate")
    )
    private val publicKeyReader = mockConstruction(PublicKeyReader::class.java) { mock, _ ->
        whenever(mock.loadPublicKey("pem")).thenReturn(publicKeyOne)
    }
    private val keyHasher = mockConstruction(KeyHasher::class.java) { mock, _ ->
        whenever(mock.hash(publicKeyOne)).thenReturn(byteArrayOf(5, 6, 7))
    }

    private val testObject = LinkManagerHostingMapImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        configuration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionTile.close()
        blockingDominoTile.close()
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
            testObject.isHostedLocally(entryOne.holdingIdentity.toCorda())
        ).isTrue
    }

    @Test
    fun `all locally hosted identities are returned correctly`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(testObject.allLocallyHostedIdentities()).containsExactlyInAnyOrder(
            entryOne.holdingIdentity.toCorda()
        )
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
                createTestHoldingIdentity(bobX500Name, "another group")
            )
        ).isFalse
    }

    @Test
    fun `onSnapshot complete the future`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(ready).isCompleted
    }

    @Test
    fun `future will not complete before onSnapshot`() {
        assertThat(ready).isNotCompleted
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
                createTestHoldingIdentity(bobX500Name, "group")
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
                createTestHoldingIdentity(bobX500Name, "group")
            )
        ).isTrue
    }

    @Test
    fun `getInfo by public key hashreturn the correct data`() {
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
                    holdingIdentity = entryOne.holdingIdentity.toCorda(),
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    sessionKeyTenantId = "id2",
                    sessionPublicKey = publicKeyOne,
                    sessionCertificates = listOf("certificate")
                )
            )
            it.assertThat(testObject.getInfo(byteArrayOf(1, 2, 2), "nop")).isNull()
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
            it.assertThat(testObject.getInfo(entryOne.holdingIdentity.toCorda())).isEqualTo(
                HostingMapListener.IdentityInfo(
                    holdingIdentity = entryOne.holdingIdentity.toCorda(),
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    sessionKeyTenantId = "id2",
                    sessionPublicKey = publicKeyOne,
                    sessionCertificates = listOf("certificate")
                )
            )
            it.assertThat(testObject.getInfo(createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"))).isNull()
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
                entryOne.holdingIdentity.toCorda(),
                entryOne.tlsCertificates,
                entryOne.tlsTenantId,
                entryOne.sessionKeyTenantId,
                publicKeyOne,
                entryOne.sessionCertificates
            )
        )
    }
}
