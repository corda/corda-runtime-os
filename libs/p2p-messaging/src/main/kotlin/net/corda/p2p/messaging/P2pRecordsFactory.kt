package net.corda.p2p.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.temporal.ChronoUnit
import java.util.UUID

class P2pRecordsFactory(
    private val clock: Clock,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    companion object {
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val MEMBERSHIP_REGISTRATION_PREFIX = "membership-registration"
        const val MEMBERSHIP_DATA_DISTRIBUTION_PREFIX = "membership-data-distribution"
        const val P2P_PREFIX = "p2p"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun SmartConfig.getTtlMinutes(ttlConfiguration: String?): Long? {
            return ttlConfiguration?.let { configurationName ->
                val path = "${MembershipConfig.TtlsConfig.TTLS}.$configurationName"
                if (this.getIsNull(path)) {
                    null
                } else {
                    this.getLong(path)
                }
            }
        }
    }

    /**
     * Creates an authenticated message for membership P2P communication. Sets the required subsytem and topic key
     * that should be used for all membership messaging.
     *
     * @param source The source of the message.
     * @param destination The destination of the message.
     * @param content The content of the message.
     * @param messageIdPrefix The prefix of the message ID. The suffix is a random [UUID] followed by '-'.
     * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
     * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
     * after which this message will be dropped from the p2p layer.
     * @param filter Optional parameter. The membership status of the member we want to send the message to.
     * If not defined active will be used. Should be only relevant from MGM's POV as MGM's can see multiple
     * statuses of a given member.
     *
     * @return The ready-to-send authenticated message record.
     */
    @Suppress("LongParameterList")
    fun <T : Any> createMembershipAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        content: T,
        messageIdPrefix: String,
        minutesToWait: Long? = null,
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
    ): Record<String, AppMessage> = createAuthenticatedMessageRecord(
        source,
        destination,
        content,
        MEMBERSHIP_P2P_SUBSYSTEM,
        messageIdPrefix,
        "Membership: $source -> $destination",
        minutesToWait,
        filter,
    )

    /**
     * Creates an authenticated message for P2P communication.
     *
     * @param source The source of the message.
     * @param destination The destination of the message.
     * @param content The content of the message.
     * @param subsystem The subsystem of the message, which will help processing of the message
     * based on filtering later on.
     * @param messageIdPrefix The prefix of the message ID. The suffix is a random [UUID] followed by '-'.
     * @param topicKey Optional parameter. The unique key per topic for a Record. If not defined a random [UUID].
     * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
     * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
     * after which this message will be dropped from the p2p layer.
     * @param filter Optional parameter. The membership status of the member we want to send the message to.
     * If not defined active will be used. Should be only relevant from MGM's POV as MGM's can see multiple
     * statuses of a given member.
     *
     * @return The ready-to-send authenticated message record.
     */
    @Suppress("LongParameterList")
    fun <T : Any> createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        content: T,
        subsystem: String,
        messageIdPrefix: String,
        topicKey: String = UUID.randomUUID().toString(),
        minutesToWait: Long? = null,
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
    ): Record<String, AppMessage> {
        val data = wrapWithNullErrorHandling({
            CordaRuntimeException("Could not serialize $content", it)
        }) {
            cordaAvroSerializationFactory.createAvroSerializer<T> {
                logger.warn("Serialization failed")
            }.serialize(content)
        }
        val header = AuthenticatedMessageHeader.newBuilder()
            .setDestination(destination)
            .setSource(source)
            .setTtl(minutesToWait?.let { clock.instant().plus(it, ChronoUnit.MINUTES) })
            .setMessageId("$messageIdPrefix-${UUID.randomUUID()}")
            .setTraceId(null)
            .setSubsystem(subsystem)
            .setStatusFilter(filter)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(header)
            .setPayload(ByteBuffer.wrap(data))
            .build()
        val appMessage = AppMessage(message)

        return Record(
            Schemas.P2P.P2P_OUT_TOPIC,
            topicKey,
            appMessage,
        )
    }
}
