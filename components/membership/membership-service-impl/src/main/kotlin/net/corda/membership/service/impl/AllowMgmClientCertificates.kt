package net.corda.membership.service.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Resource
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

internal class AllowMgmClientCertificates(
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
): Resource {
    private val subscription by lazy {
        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                "allow-mgm-client-certificates",
                Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
            ),
            Processor(),
            messagingConfig,
        )
    }
    fun start() {
        // YIFT: This need to run only in mTLS mode
        subscription.start()
    }

    fun getMgmCertificates(mgm: HoldingIdentity): Collection<String> {
        // YIFT: This is a hack
        return getMgmCertificates(mgm, 0)
    }

    fun getMgmCertificates(mgm: HoldingIdentity, retries: Int): Collection<String> {
        val subjects = clientCertificatesSubjects[mgm]
        if(subjects == null) {
            if(retries > 100) {
                throw CordaRuntimeException("Waiting for MGM hosted identity for too long")
            }
            println("Mgm is not ready, waiting a second...")
            Thread.sleep(1000)
            return getMgmCertificates(mgm, retries + 1)
        }
        return subjects
    }

    private val clientCertificatesSubjects = ConcurrentHashMap<HoldingIdentity, Collection<String>>()

    private inner class Processor : CompactedProcessor<String, HostedIdentityEntry> {
        override val keyClass = String::class.java
        override val valueClass = HostedIdentityEntry::class.java

        override fun onNext(
            newRecord: Record<String, HostedIdentityEntry>,
            oldValue: HostedIdentityEntry?,
            currentData: Map<String, HostedIdentityEntry>,
        ) {
            newRecord.value.also {
                if(it != null) {
                    addData(it)
                } else {
                    oldValue?.also { value ->
                        clientCertificatesSubjects.remove(value.holdingIdentity.toCorda())
                    }
                }
            }
        }

        override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
            currentData.values.forEach {
                addData(it)
            }
        }

    }

    private fun addData(value: HostedIdentityEntry) {
        value.tlsClientCertificates?.flatMap { pemCertificate ->
            ByteArrayInputStream(pemCertificate.toByteArray()).use {
                certificateFactory.generateCertificates(it)
            }.filterIsInstance<X509Certificate>()
                .map { it.subjectX500Principal }
                .map { MemberX500Name.build(it) }
                .map { it.toString() }
        }?.also { certificates ->
            clientCertificatesSubjects[value.holdingIdentity.toCorda()] = certificates
        }
    }

    override fun close() {
        subscription.close()
    }
}