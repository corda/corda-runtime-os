package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS

@Suppress("LongParameterList")
internal class ClientCertificateBusListener<T : Any> private constructor(
    override val valueClass: Class<T>,
    private val converter: (Record<String, T>) -> Record<String, ClientCertificateSubjects>,
) : DurableProcessor<String, T> {
    companion object {
        private const val LISTENER_NAME = "certificate-subject-forwarding-subscription"

        inline fun <reified T : Any> createSubscription(
            lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
            messagingConfiguration: SmartConfig,
            subscriptionFactory: SubscriptionFactory,
            topic: String,
            crossinline subjectFactory: (T) -> String,
        ): DominoTile {
            val subscriptionConfig = SubscriptionConfig(
                groupName = "$LISTENER_NAME-${T::class.java.simpleName}",
                eventTopic = topic,
            )
            val processor = ClientCertificateBusListener(
                T::class.java,
            ) { record ->
                val value = record.value?.let(subjectFactory)?.let {
                    ClientCertificateSubjects(it)
                }
                val key = "$topic-${record.key}"
                Record(
                    GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                    key,
                    value
                )
            }

            return SubscriptionDominoTile(
                coordinatorFactory = lifecycleCoordinatorFactory,
                subscriptionConfig = subscriptionConfig,
                managedChildren = emptyList(),
                dependentChildren = emptyList(),
                subscriptionGenerator = {
                    subscriptionFactory.createDurableSubscription(
                        subscriptionConfig = subscriptionConfig,
                        partitionAssignmentListener = null,
                        processor = processor,
                        messagingConfig = messagingConfiguration,
                    )
                }
            )
        }
    }

    override fun onNext(events: List<Record<String, T>>): List<Record<*, *>> {
        return events.map { converter(it) }
    }

    override val keyClass = String::class.java
}
