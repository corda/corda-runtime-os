package net.corda.p2p.gateway.messaging.mtls

import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import java.util.concurrent.ConcurrentHashMap

class DynamicCertificateSubjectStore(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
): LifecycleWithDominoTile {
    private companion object {
        const val CONSUMER_GROUP_ID = "gateway_certificates_allowed_client_subjects_reader"
    }

    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS)
    private val certificateSubjects = ConcurrentHashMap<CertificateSubject, MutableSet<String>>()
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration
        )
    }
    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )

    fun subjectAllowed(subject: CertificateSubject): Boolean {
        return certificateSubjects.containsKey(subject)
    }

    private fun addSource(subject: CertificateSubject, source: String) {
        certificateSubjects.compute(subject) { _, sources ->
            sources?.apply { add(source) } ?: mutableSetOf(source)
        }
    }

    private fun removeSource(subject: CertificateSubject, source: String) {
        certificateSubjects.computeIfPresent(subject) { _, sources ->
            sources.remove(source)
            sources.ifEmpty {
                null
            }
        }
    }

    private inner class Processor : CompactedProcessor<String, ClientCertificateSubjects> {
        override val keyClass = String::class.java
        override val valueClass = ClientCertificateSubjects::class.java

        override fun onNext(
            newRecord: Record<String, ClientCertificateSubjects>,
            oldValue: ClientCertificateSubjects?,
            currentData: Map<String, ClientCertificateSubjects>
        ) {
            val newCertificateSubject = newRecord.value?.subject
            val oldCertificateSubjects = oldValue?.subject
            val source = newRecord.key
            if (newCertificateSubject != null) {
                addSource(newCertificateSubject, source)
            } else if (oldCertificateSubjects != null) {
                removeSource(oldCertificateSubjects, source)
            }
        }

        override fun onSnapshot(currentData: Map<String, ClientCertificateSubjects>) {
            currentData.forEach { (source, value) ->
                addSource(value.subject, source)
            }
        }
    }
}

typealias CertificateSubject = String