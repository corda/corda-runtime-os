package net.corda.cordapptestutils.internal.testflows

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow

class NonSuspendableFlow : RPCStartableFlow {
    override fun call(requestBody: RPCRequestData): String = ""
}