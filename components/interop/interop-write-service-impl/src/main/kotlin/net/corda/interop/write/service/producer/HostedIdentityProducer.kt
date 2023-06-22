package net.corda.interop.write.service.producer

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.interop.write.service.data.AliasIdentity
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class HostedIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()
    }

    fun publishHostedAliasIdentity(aliasIdentity: AliasIdentity) {
        val record = createHostedAliasIdentity(aliasIdentity)

        if (publisher.get() == null) {
            logger.error("Interop hosted identity publisher is null, not publishing.")
            return
        }
        publisher.get()!!.publish(listOf(record))
        logger.info("Interop hosted identity published with key : ${aliasIdentity.aliasShortHash.value} and value : $record")
    }

    private fun createHostedAliasIdentity(aliasIdentity: AliasIdentity): Record<String, HostedIdentityEntry> {
        val hostedIdentity = HostedIdentityEntry(
            HoldingIdentity(aliasIdentity.x500Name.toString(), aliasIdentity.groupId.toString()),
            aliasIdentity.aliasShortHash.value,
            listOf(DUMMY_CERTIFICATE),
            HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
            emptyList()
        )
        return Record(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC, aliasIdentity.aliasShortHash.value, hostedIdentity)
    }
}
