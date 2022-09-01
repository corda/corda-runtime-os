package net.corda.cordapptestutils.internal.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.BlockingQueue

class BlockingQueueFlowSession(
    private val owner: MemberX500Name,
    override val counterparty: MemberX500Name,
    private val flowClass: Class<out Flow>,
    private val from: BlockingQueue<Any>,
    private val to: BlockingQueue<Any>
) : FlowSession {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun getCounterpartyFlowInfo(): FlowInfo {
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