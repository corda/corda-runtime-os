package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.utilities.time.Clock
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.schema.Schemas

internal class EstablishedSessionRecorder(
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val clock: Clock,
) {
    fun recordsForSessionEstablished(
        sessionManager: SessionManager,
        session: Session,
        serial: Long,
        messageAndKey: AuthenticatedMessageAndKey,
    ): List<Record<String, *>> {
        return MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(
            messageAndKey,
            session,
            groupPolicyProvider,
            membershipGroupReaderProvider,
            serial
        )?.let { message ->
            val key = LinkManager.generateKey()
            val messageRecord = Record(Schemas.P2P.LINK_OUT_TOPIC, key, message)
            val marker = AppMessageMarker(LinkManagerSentMarker(), clock.instant().toEpochMilli())
            val markerRecord = Record(Schemas.P2P.P2P_OUT_MARKERS, messageAndKey.message.header.messageId, marker)
            sessionManager.dataMessageSent(session)
            listOf(
                messageRecord,
                markerRecord,
            )
        } ?: emptyList()
    }
}
