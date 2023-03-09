package net.corda.flow.application.sessions

import net.corda.flow.pipeline.sessions.protocol.FlowAndProtocolVersion
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class FlowInfoImpl(private val protocolVersion: FlowAndProtocolVersion) : FlowInfo {
    override fun protocol(): String {
        return protocolVersion.protocol
    }

    override fun protocolVersion(): Int {
        return protocolVersion.protocolVersion
    }
}
