package net.corda.p2p.gateway.messaging

import net.corda.v5.base.util.NetworkHostAndPort

class ReceivedMessage(
    override var payload: ByteArray,
    override val source: NetworkHostAndPort?,
    override val destination: NetworkHostAndPort?
) : ApplicationMessage {
    override fun release() {
        payload = ByteArray(0)
    }
}