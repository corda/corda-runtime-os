package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock

class ClientCertificateBusListenerTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val messagingConfiguration = mock<SmartConfig>()
    private val processor = argumentCaptor<DurableProcessor<String, Value>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createDurableSubscription(
                any(),
                processor.capture(),
                eq(messagingConfiguration),
                isNull()
            )
        } doReturn mock()
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        val subscriptionBuilder = context.arguments()[1] as? () -> Subscription<String, Value>
        subscriptionBuilder?.invoke()
    }

    internal data class Value(
        val subject: String
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
    }

    @BeforeEach
    fun setUp() {
        ClientCertificateBusListener.createSubscription(
            lifecycleCoordinatorFactory,
            messagingConfiguration,
            subscriptionFactory,
            "topic",
            Value::subject
        )
    }

    @Test
    fun `onNext add prefix to the key`() {
        val keys = processor.firstValue.onNext(
            listOf(
                Record(
                    "topic",
                    "key-1",
                    Value("subject-1")
                ),
                Record(
                    "topic",
                    "key-2",
                    Value("subject-2")
                ),
                Record(
                    "topic",
                    "key-3",
                    null
                ),
            )
        ).map { it.key }

        assertThat(keys).containsExactly("topic-key-1", "topic-key-2", "topic-key-3")
    }

    @Test
    fun `onNext publish the subject if its not null`() {
        val subjects = processor.firstValue.onNext(
            listOf(
                Record(
                    "topic",
                    "key-1",
                    Value("subject-1")
                ),
                Record(
                    "topic",
                    "key-2",
                    Value("subject-2")
                ),
            )
        )
            .map { it.value }
            .map { it as? ClientCertificateSubjects }
            .map { it?.subject }

        assertThat(subjects)
            .containsExactly("subject-1", "subject-2")
    }

    @Test
    fun `onNext publish null if it is null`() {
        val subjects = processor.firstValue.onNext(
            listOf(
                Record(
                    "topic",
                    "key-1",
                    null
                ),
                Record(
                    "topic",
                    "key-2",
                    null
                ),
            )
        )
            .map { it.value }
            .map { it as? Value }
            .map { it?.subject }

        assertThat(subjects)
            .containsExactly(null, null)
    }
}
