package net.corda.interop.web3j

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

class Web3jProcessor(
): DurableProcessor<String, String> {

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        val web3j = Web3j.build()
        TODO("Not yet implemented")
    }
}