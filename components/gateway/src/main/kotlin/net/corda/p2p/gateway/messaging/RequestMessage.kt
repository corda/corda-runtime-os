//package net.corda.p2p.gateway.messaging
//
//import io.netty.handler.codec.http.HttpResponse
//import net.corda.v5.base.util.NetworkHostAndPort
//
///**
// * [ApplicationMessage] implementation. Used to pass a received P2P message from the transport layer to application layer.
// */
//class HttpMessage(
//    val response: HttpResponse,
//    override var payload: ByteArray,
//    override val source: NetworkHostAndPort,
//    override val destination: NetworkHostAndPort
//) : ApplicationMessage {
//    override fun release() {
//        payload = ByteArray(0)
//    }
//}