package net.corda.rpc.server

interface RPCEndpointManager {
    fun <REQ: Any, RESP: Any> registerEndpoint(endpoint: String, handler: (REQ) -> RESP, clazz: Class<REQ>)
}