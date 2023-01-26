package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ClientCertificateMembersListListenerTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val messagingConfiguration = mock<SmartConfig>()
    private val processor = argumentCaptor<CompactedProcessor<String, MemberAllowedCertificateSubject>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any()
            )
        } doReturn mock()
    }
    private val clientCertificateSourceManager = mock<ClientCertificateSourceManager>()
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        val subscriptionBuilder = context.arguments()[1] as? () -> Subscription<String, MemberAllowedCertificateSubject>
        subscriptionBuilder?.invoke()
    }

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
    }

    @BeforeEach
    fun setUp() {
        ClientCertificateMembersListListener(
            lifecycleCoordinatorFactory,
            messagingConfiguration,
            subscriptionFactory,
            clientCertificateSourceManager
        )
    }

    @Test
    fun `onNext will add source if not null`() {
        processor.firstValue.onNext(
            Record(
                "topic",
                "key",
                MemberAllowedCertificateSubject("subject")
            ),
            null,
            emptyMap(),
        )

        verify(clientCertificateSourceManager).addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key"),
        )
    }

    @Test
    fun `onNext will remove source if not removed`() {
        processor.firstValue.onNext(
            Record(
                "topic",
                "key",
                null,
            ),
            MemberAllowedCertificateSubject("subject"),
            emptyMap(),
        )

        verify(clientCertificateSourceManager).removeSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key"),
        )
    }

    @Test
    fun `onNext will do nothing if there is nothing to do`() {
        processor.firstValue.onNext(
            Record(
                "topic",
                "key",
                null,
            ),
            null,
            emptyMap(),
        )

        verifyNoInteractions(clientCertificateSourceManager)
    }

    @Test
    fun `onSnapshot will add all the subjects`() {
        val subjects = (1..4).associate { "key1" to MemberAllowedCertificateSubject("subject 1") }

        processor.firstValue.onSnapshot(subjects)

        subjects.forEach { (key, subject) ->
            verify(clientCertificateSourceManager)
                .addSource(
                    subject.subject,
                    ClientCertificateSourceManager.MembershipSource(key)
                )
        }
    }
}
