package net.corda.messaging.api.processor


interface HttpRPCProcessor<REQ, RESP> {
    fun process(request: REQ) : RESP

    val reqClazz: Class<REQ>
    val respClazz: Class<RESP>
}
