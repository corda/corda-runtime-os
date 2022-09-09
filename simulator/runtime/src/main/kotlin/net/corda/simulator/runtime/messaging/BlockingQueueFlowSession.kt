package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.BlockingQueue

class BlockingQueueFlowSession(
    override val counterparty: MemberX500Name,
    private val from: BlockingQueue<Any>,
    private val to: BlockingQueue<Any>
) : FlowSession {
    override fun close() {
        TODO("Not yet implemented")
    }

    override val contextProperties: FlowContextProperties
        get() {
            TODO("Not yet implemented")
        }

    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        try {
            @Suppress("UNCHECKED_CAST")
            return UntrustworthyData(to.take()) as UntrustworthyData<R>
        } catch (e: ClassCastException) {
            throw IllegalStateException("Message on queue was not a ${receiveType.simpleName}")
        }
    }

    override fun send(payload: Any) {
        from.put(payload)
    }

    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        send(payload)
        return receive(receiveType)
    }
}
