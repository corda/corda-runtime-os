package net.corda.messaging.api.processor


interface HttpRPCProcessor<REQ : Any, RESP : Any> {
    fun process(request: REQ) : RESP

    val reqClazz: Class<REQ>
    val respClazz: Class<RESP>
}
