package net.corda.flow.application.sessions.impl

import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.base.annotations.CordaSerializable

/**
 * Object used to store flow information.
 * @param protocol the protocol this flow is using
 * @param protocolVersion the protocol version this flow is running
 */
@CordaSerializable
class FlowInfoImpl(private val protocol: String, private val protocolVersion: Int) : FlowInfo {
    override fun protocol(): String {
        return protocol
    }

    override fun protocolVersion(): Int {
        return protocolVersion
    }
}