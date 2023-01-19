package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.utils.accessField
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.util.contextLogger
import java.io.Closeable

/**
 * See [FlowManager].
 */
class BaseFlowManager : FlowManager {

    companion object {
        val log = contextLogger()
    }

    override fun call(requestData: RestRequestBody, flow: ClientStartableFlow) : String {
        val result = flow.call(requestData)
        closeFlowMessaging(flow)
        return result
    }

    private fun closeFlowMessaging(flow: Flow) {
        val flowMessagingField = flow.accessField(FlowMessaging::class.java)
        val flowMessaging = flowMessagingField?.get(flow) as Closeable?

        if (flowMessaging != null) {
            log.info("FlowMessaging found on flow ${flow.javaClass.simpleName} - closing down")
            flowMessaging.close()
            log.info("FlowMessaging on flow ${flow.javaClass.simpleName} closed")
        }
    }

    override fun <R> call(subFlow: SubFlow<R>): R {
        val result = subFlow.call()
        closeFlowMessaging(subFlow)
        return result
    }
}
