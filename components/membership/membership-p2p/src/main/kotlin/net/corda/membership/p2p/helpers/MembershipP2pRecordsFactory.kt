package net.corda.membership.p2p.helpers

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.util.UUID

class MembershipP2pRecordsFactory(
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory,
) {
    companion object {
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun SmartConfig.getTtlMinutes(ttlConfiguration: String?): Long? {
            return ttlConfiguration?.let { configurationName ->
                val path = "$TTLS.$configurationName"
                if (this.getIsNull(path)) {
                    null
                } else {
                    this.getLong(path)
                }
            }
        }
    }

    /**
     * Creates an authenticated message for P2P communication.
     *
     * @param source The source of the message.
     * @param destination The destination of the message.
     * @param content The content of the message.
     * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
     * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
     * after which this message will be dropped from the p2p layer.
     *
     * @return The ready-to-send authenticated message record.
     */
    @Suppress("LongParameterList")
    fun <T : Any> createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        content: T,
        minutesToWait: Long? = null,
        id: String = UUID.randomUUID().toString(),
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
    ): Record<String, AppMessage> {
        val data = wrapWithNullErrorHandling({
            CordaRuntimeException("Could not serialize $content", it)
        }) {
            cordaAvroSerializationFactory.createAvroSerializer<T> {
                logger.warn("Serialization failed")
            }.serialize(content)
        }
        return p2pRecordsFactory.createAuthenticatedMessageRecord(
            source,
            destination,
            data,
            MEMBERSHIP_P2P_SUBSYSTEM,
            "Membership: $source -> $destination",
            minutesToWait,
            id,
            filter,
        )
    }
}
