package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.AllowedCertificateSubject
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

class ClientCertificateAllowedListListenerTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val messagingConfiguration = mock<SmartConfig>()
    private val processor = argumentCaptor<CompactedProcessor<String, AllowedCertificateSubject>>()
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
        val subscriptionBuilder = context.arguments()[1] as? () -> Subscription<String, AllowedCertificateSubject>
        subscriptionBuilder?.invoke()
    }

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
    }

    @BeforeEach
    fun setUp() {
        ClientCertificateAllowedListListener(
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
                "subject",
                AllowedCertificateSubject("subject")
            ),
            null,
            emptyMap(),
        )

        verify(clientCertificateSourceManager).addSource(
            "subject",
            ClientCertificateSourceManager.MgmAllowedListSource
        )
    }

    @Test
    fun `onNext will remove source if not removed`() {
        processor.firstValue.onNext(
            Record(
                "topic",
                "subject",
                null,
            ),
            AllowedCertificateSubject("subject"),
            emptyMap(),
        )

        verify(clientCertificateSourceManager).removeSource(
            "subject",
            ClientCertificateSourceManager.MgmAllowedListSource
        )
    }

    @Test
    fun `onSnapshot will add all the subjects`() {
        val subjects = (1..4).map { "subject-$it" }
        processor.firstValue.onSnapshot(
            subjects.associateWith { AllowedCertificateSubject(it) }
        )

        subjects.forEach {
            verify(clientCertificateSourceManager)
                .addSource(it, ClientCertificateSourceManager.MgmAllowedListSource)
        }
    }
}
