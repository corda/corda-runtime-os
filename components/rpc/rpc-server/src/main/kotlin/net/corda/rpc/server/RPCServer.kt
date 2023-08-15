package net.corda.rpc.server

interface RPCServer {
    fun <REQ: Any, RESP: Any> registerEndpoint(endpoint: String, handler: (REQ) -> RESP, clazz: Class<REQ>)
}