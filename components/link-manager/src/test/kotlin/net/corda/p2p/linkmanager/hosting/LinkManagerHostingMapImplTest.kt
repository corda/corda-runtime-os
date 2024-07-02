package net.corda.p2p.linkmanager.hosting

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.KeyHasher
import net.corda.p2p.linkmanager.common.PublicKeyReader
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
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
    private val publicKeyTwo = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(3)
    }
    private val publicKeyThree = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(4, 5)
    }
    private val bobX500Name = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
    private val bobHoldingIdentity = createTestHoldingIdentity(bobX500Name, "group")
    private val memberContext = mock<MemberContext> {
        on { parse(GROUP_ID, String::class.java) } doReturn bobHoldingIdentity.groupId
        on { parseList(SESSION_KEYS, PublicKey::class.java) } doReturn listOf(publicKeyOne)
    }
    private val entryOneMemberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn memberContext
        on { mgmProvidedContext } doReturn mock()
        on { name } doReturn MemberX500Name.parse(bobX500Name)
    }
    private val entryOne = HostedIdentityEntry(
        bobHoldingIdentity.toAvro(),
        "id1",
        listOf("cert1", "cert2"),
        HostedIdentitySessionKeyAndCert(
            "pem",
            listOf("certificate")
        ),
        emptyList(),
        1
    )
    private val publicKeyReader = mockConstruction(PublicKeyReader::class.java) { mock, _ ->
        whenever(mock.loadPublicKey("pem")).thenReturn(publicKeyOne)
        whenever(mock.loadPublicKey("pem1")).thenReturn(publicKeyOne)
        whenever(mock.loadPublicKey("pem2")).thenReturn(publicKeyTwo)
        whenever(mock.loadPublicKey("pem3")).thenReturn(publicKeyThree)
    }
    private val keyHasher = mockConstruction(KeyHasher::class.java) { mock, _ ->
        whenever(mock.hash(publicKeyOne)).thenReturn(byteArrayOf(5, 6, 7))
        whenever(mock.hash(publicKeyTwo)).thenReturn(byteArrayOf(8, 9))
        whenever(mock.hash(publicKeyThree)).thenReturn(byteArrayOf(10))
    }

    private val testObject = LinkManagerHostingMapImpl(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        configuration,
        mock()
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
    fun `getInfo by public key hash return the correct data`() {
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
                    HostingMapListener.SessionKeyAndCertificates(
                        sessionPublicKey = publicKeyOne,
                        sessionCertificateChain = listOf("certificate")
                    ),
                    emptyList(),
                )
            )
            it.assertThat(testObject.getInfo(byteArrayOf(1, 2, 2), "nop")).isNull()
        }
    }

    @Test
    fun `getInfo by public key hash return the correct data for alternative key`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to HostedIdentityEntry(
                    createTestHoldingIdentity(bobX500Name, "group").toAvro(),
                    "id1",
                    listOf("cert1", "cert2"),
                    HostedIdentitySessionKeyAndCert(
                        "pem1",
                        listOf("certificate1")
                    ),
                    listOf(
                        HostedIdentitySessionKeyAndCert(
                            "pem2",
                            listOf("certificate2")
                        ),
                        HostedIdentitySessionKeyAndCert(
                            "pem3",
                            listOf("certificate3")
                        ),
                    ),
                    1
                )
            )
        )

        assertSoftly {
            it.assertThat(
                testObject.getInfo(
                    byteArrayOf(10),
                    entryOne.holdingIdentity.groupId,
                )
            ).isEqualTo(
                HostingMapListener.IdentityInfo(
                    holdingIdentity = entryOne.holdingIdentity.toCorda(),
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    HostingMapListener.SessionKeyAndCertificates(
                        sessionPublicKey = publicKeyOne,
                        sessionCertificateChain = listOf("certificate1")
                    ),
                    listOf(
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyTwo,
                            listOf("certificate2")
                        ),
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyThree,
                            listOf("certificate3")
                        ),
                    )
                )
            )
            it.assertThat(
                testObject.getInfo(
                    byteArrayOf(8, 9),
                    entryOne.holdingIdentity.groupId,
                )
            ).isEqualTo(
                HostingMapListener.IdentityInfo(
                    holdingIdentity = entryOne.holdingIdentity.toCorda(),
                    tlsCertificates = listOf("cert1", "cert2"),
                    tlsTenantId = "id1",
                    HostingMapListener.SessionKeyAndCertificates(
                        sessionPublicKey = publicKeyOne,
                        sessionCertificateChain = listOf("certificate1")
                    ),
                    listOf(
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyTwo,
                            listOf("certificate2")
                        ),
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyThree,
                            listOf("certificate3")
                        ),
                    )
                )
            )
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
                    HostingMapListener.SessionKeyAndCertificates(
                        sessionPublicKey = publicKeyOne,
                        sessionCertificateChain = listOf("certificate1")
                    ),
                    listOf(
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyTwo,
                            listOf("certificate2")
                        ),
                        HostingMapListener.SessionKeyAndCertificates(
                            publicKeyThree,
                            listOf("certificate3")
                        ),
                    )
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
                    HostingMapListener.SessionKeyAndCertificates(
                        sessionPublicKey = publicKeyOne,
                        sessionCertificateChain = listOf("certificate")
                    ),
                    emptyList(),
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
                HostingMapListener.SessionKeyAndCertificates(
                    publicKeyOne,
                    entryOne.preferredSessionKeyAndCert.sessionCertificates
                ),
                emptyList(),
            )
        )
    }

    @Test
    fun `isHostedLocallyAndSessionKeyMatch returns true for session key matches`() {
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(
            testObject.isHostedLocallyAndSessionKeyMatch(entryOneMemberInfo)
        ).isTrue
    }

    @Test
    fun `isHostedLocallyAndSessionKeyMatch returns false if no session keys match`() {
        val mockMemberContext = mock<MemberContext> {
            on { parse(GROUP_ID, String::class.java) } doReturn "another group"
        }
        val otherMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { mgmProvidedContext } doReturn mock()
            on { name } doReturn MemberX500Name.parse(bobX500Name)
        }
        processor.firstValue.onSnapshot(
            mapOf(
                "key" to entryOne
            )
        )

        assertThat(
            testObject.isHostedLocallyAndSessionKeyMatch(
                otherMemberInfo
            )
        ).isFalse
    }
}
