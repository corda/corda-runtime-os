package net.corda.applications.workers.workercommon

interface RPCSubscription {
    fun <REQ: Any, RESP: Any> registerEndpoint(endpoint: String, handler: (REQ) -> RESP, clazz: Class<REQ>)
}