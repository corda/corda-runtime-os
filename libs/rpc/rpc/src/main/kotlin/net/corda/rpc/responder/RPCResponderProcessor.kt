package net.corda.rpc.responder

import net.corda.messaging.api.records.Record

interface RPCResponderProcessor {

    /**
     * The implementation of this functional class will be used to notify you of any requests that need processing
     * @param record that was received
     */
    fun onUpdate(record: Record<String, String>)
}