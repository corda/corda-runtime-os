package net.corda.membership.lib

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import net.corda.schema.configuration.MembershipConfig
import java.util.UUID

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

/**
 * Creates the record's key for membership messages. Since we use compacted topics, using the same key per process
 * will guarantee that the same worker will execute a given membership process.
 *
 * @param source The source of the message.
 * @param destination The destination of the message.
 *
 * @return The key which should be used for processing.
 */
fun getMembershipRecordKey(source: HoldingIdentity, destination: HoldingIdentity) = "Membership: $source -> $destination"

/**
 * Creates an authenticated message for membership P2P communication. Sets the required subsytem and topic key
 * that should be used for all membership messaging.
 *
 * @param source The source of the message.
 * @param destination The destination of the message.
 * @param content The content of the message.
 * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
 * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
 * after which this message will be dropped from the p2p layer.
 * @param messageId Optional parameter. The ID of the message. If not defined a random [UUID].
 * @param filter Optional parameter. The membership status of the member we want to send the message to.
 * If not defined active will be used. Should be only relevant from MGM's POV as MGM's can see multiple
 * statuses of a given member.
 *
 * @return The ready-to-send authenticated message record.
 */
@Suppress("LongParameterList")
fun <T : Any> P2pRecordsFactory.createMembershipAuthenticatedMessageRecord(
    source: HoldingIdentity,
    destination: HoldingIdentity,
    content: T,
    minutesToWait: Long? = null,
    messageId: String = UUID.randomUUID().toString(),
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = createAuthenticatedMessageRecord(
    source,
    destination,
    content,
    Subsystem.MEMBERSHIP,
    getMembershipRecordKey(source, destination),
    minutesToWait,
    messageId,
    filter,
)