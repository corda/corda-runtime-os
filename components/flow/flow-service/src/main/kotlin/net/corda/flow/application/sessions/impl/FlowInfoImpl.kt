package net.corda.flow.application.sessions.impl

import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class FlowInfoImpl(private val protocol: String, private val protocolVersion: Int) : FlowInfo {
    override fun protocol(): String {
        return protocol
    }

    override fun protocolVersion(): Int {
        return protocolVersion
    }
}