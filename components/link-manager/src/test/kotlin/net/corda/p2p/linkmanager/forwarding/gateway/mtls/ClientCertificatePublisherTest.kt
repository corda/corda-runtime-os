package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.AllClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.Companion.P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClientCertificatePublisherTest {
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val messagingConfiguration = mock<SmartConfig>()
    private val processor = argumentCaptor<CompactedProcessor<String, AllClientCertificateSubjects>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                argThat {
                    this.eventTopic == P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
                },
                processor.capture(),
                any()
            )
        } doReturn mock()
    }
    private val publisherFactory = mock<PublisherFactory>()
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        val subscriptionBuilder = context.arguments()[1] as? () -> Subscription<String, AllClientCertificateSubjects>
        subscriptionBuilder?.invoke()
    }
    private val mockPublisherWithDominoLogic = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
        whenever(mock.dominoTile).doReturn(mock())
    }

    private val clientCertificatePublisher = ClientCertificatePublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        mockPublisherWithDominoLogic.close()
    }

    @Nested
    inner class GroupAddedTests {
        private val holdingIdentity = createTestHoldingIdentity(
            "CN=Alice, O=Bob Corp, L=LDN, C=GB",
            "Group1",
        )
        private val mgmClientCertificateSubject =
            MemberX500Name.parse("CN=Group policy client, O=Bob Corp, L=LDN, C=GB")
        private val p2pParametersWithClientCertificate = mock<GroupPolicy.P2PParameters> {
            on { mgmClientCertificateSubject } doReturn mgmClientCertificateSubject
        }
        private val groupPolicyWithClientCertificate = mock<GroupPolicy> {
            on { p2pParameters } doReturn p2pParametersWithClientCertificate
        }

        @Test
        fun `groupAdded will publish the group for the first time`() {
            processor.firstValue.onSnapshot(emptyMap())

            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )

            verify(mockPublisherWithDominoLogic.constructed().first()).publish(
                listOf(
                    Record(
                        topic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        key = mgmClientCertificateSubject.toString(),
                        value = AllClientCertificateSubjects(mgmClientCertificateSubject.toString())
                    )
                )
            )
        }

        @Test
        fun `groupAdded will remove the group if the certificate had changed`() {
            val secondMgmClientCertificateSubject = MemberX500Name.parse("CN=Another Client, O=Bob Corp, L=LDN, C=GB")
            processor.firstValue.onSnapshot(emptyMap())
            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )
            whenever(p2pParametersWithClientCertificate.mgmClientCertificateSubject)
                .thenReturn(secondMgmClientCertificateSubject)

            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )

            verify(mockPublisherWithDominoLogic.constructed().first()).publish(
                listOf(
                    Record(
                        topic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        key = mgmClientCertificateSubject.toString(),
                        value = null,
                    )
                )
            )
        }

        @Test
        fun `groupAdded will do nothing if there is no subject`() {
            processor.firstValue.onSnapshot(emptyMap())
            whenever(p2pParametersWithClientCertificate.mgmClientCertificateSubject)
                .thenReturn(null)

            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )

            verify(mockPublisherWithDominoLogic.constructed().first(), never()).publish(any())
        }

        @Test
        fun `groupAdded will remove the group if the certificate became null`() {
            processor.firstValue.onSnapshot(emptyMap())
            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )
            whenever(p2pParametersWithClientCertificate.mgmClientCertificateSubject)
                .thenReturn(null)

            clientCertificatePublisher.groupAdded(
                holdingIdentity,
                groupPolicyWithClientCertificate
            )

            verify(mockPublisherWithDominoLogic.constructed().first()).publish(
                listOf(
                    Record(
                        topic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        key = mgmClientCertificateSubject.toString(),
                        value = null,
                    )
                )
            )
        }
    }

    @Nested
    inner class PublishSubjectTest {
        @Test
        fun `it will publish the subject if needed`() {
            processor.firstValue.onSnapshot(emptyMap())

            clientCertificatePublisher.publishSubject("subject")

            verify(mockPublisherWithDominoLogic.constructed().first()).publish(
                listOf(
                    Record(
                        topic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                        key = "subject",
                        value = AllClientCertificateSubjects("subject"),
                    )
                )
            )
        }

        @Test
        fun `it will not publish the subject the snapshot is not ready`() {
            clientCertificatePublisher.publishSubject("subject")

            verify(mockPublisherWithDominoLogic.constructed().first(), never()).publish(any())
        }

        @Test
        fun `it will not publish the subject the publisher is not running`() {
            processor.firstValue.onSnapshot(emptyMap())
            whenever(mockPublisherWithDominoLogic.constructed().first().isRunning).doReturn(false)

            clientCertificatePublisher.publishSubject("subject")

            verify(mockPublisherWithDominoLogic.constructed().first(), never()).publish(any())
        }

        @Test
        fun `it will not publish the subject if it has been published`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "subject" to AllClientCertificateSubjects("subject")
                )
            )

            clientCertificatePublisher.publishSubject("subject")

            verify(mockPublisherWithDominoLogic.constructed().first(), never()).publish(any())
        }
    }

    @Nested
    inner class OnNextTest {
        @Test
        fun `it will keep the subject if valid`() {
            processor.firstValue.onSnapshot(emptyMap())

            processor.firstValue.onNext(
                Record(
                    "topic",
                    "subject",
                    AllClientCertificateSubjects("subject")
                ),
                null,
                emptyMap()
            )

            clientCertificatePublisher.publishSubject("subject")
            verify(mockPublisherWithDominoLogic.constructed().first(), never()).publish(any())
        }

        @Test
        fun `it will remove the subject if null`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "subject" to AllClientCertificateSubjects("subject")
                )
            )

            processor.firstValue.onNext(
                Record(
                    "topic",
                    "subject",
                    null,
                ),
                AllClientCertificateSubjects("subject"),
                emptyMap()
            )

            clientCertificatePublisher.publishSubject("subject")
            verify(mockPublisherWithDominoLogic.constructed().first())
                .publish(
                    listOf(
                        Record(
                            topic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                            key = "subject",
                            value = AllClientCertificateSubjects("subject")
                        )
                    )
                )
        }

        @Test
        fun `it will not remove the subject if previous data is null`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "subject" to AllClientCertificateSubjects("subject")
                )
            )

            processor.firstValue.onNext(
                Record(
                    "topic",
                    "subject",
                    null,
                ),
                null,
                emptyMap()
            )

            clientCertificatePublisher.publishSubject("subject")
            verify(mockPublisherWithDominoLogic.constructed().first(), never())
                .publish(any())
        }
    }
}
