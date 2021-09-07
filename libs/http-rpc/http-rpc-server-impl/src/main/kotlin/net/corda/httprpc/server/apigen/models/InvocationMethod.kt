package net.corda.httprpc.server.apigen.models

import net.corda.v5.application.messaging.RPCOps
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
        val method: Method,
        val instance: RPCOps
)