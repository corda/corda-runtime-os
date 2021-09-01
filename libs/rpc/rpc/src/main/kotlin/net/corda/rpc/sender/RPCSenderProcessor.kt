package net.corda.rpc.sender

import net.corda.messaging.api.records.Record


interface RPCSenderProcessor {

    /**
     * The implementation of this functional class will be used to notify you of any responses
     * @param record that was received
     */
    fun onUpdate(record: Record<String, String>)
}