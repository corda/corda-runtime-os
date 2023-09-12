package net.corda.flow.mapper.impl.executor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.flow.utils.isInitiatedIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.SESSION_P2P_TTL
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_SUBSYSTEM
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

/**
 * Generate and return random ID for flowId
 * @return a new flow id
 */
fun generateFlowId(): String {
    return UUID.randomUUID().toString()
}
