package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.ConcurrentHashMap

internal class ClientCertificateSourceManager(
    private val listener: Listener
) {
    sealed interface KnownCertificateSource

    data class MgmAllowedListSource(
        val groupId: String,
    ) : KnownCertificateSource

    data class GroupPolicySource(
        val holdingIdentity: HoldingIdentity,
    ) : KnownCertificateSource

    data class MembershipSource(
        val key: String
    ) : KnownCertificateSource

    interface Listener {
        fun publishSubject(subject: String)
        fun removeSubject(subject: String)
    }

    private val knownCertificates = ConcurrentHashMap<String, MutableSet<KnownCertificateSource>>()

    fun addSource(
        certificateSubject: String,
        source: KnownCertificateSource
    ) {
        knownCertificates.computeIfAbsent(certificateSubject) { _ ->
            listener.publishSubject(certificateSubject)
            ConcurrentHashMap.newKeySet()
        }.add(source)
    }

    fun removeSource(
        certificateSubject: String,
        source: KnownCertificateSource
    ) {
        knownCertificates.computeIfPresent(certificateSubject) { _, sources ->
            sources.remove(source)
            if (sources.isEmpty()) {
                listener.removeSubject(certificateSubject)
                null
            } else {
                sources
            }
        }
    }
}
