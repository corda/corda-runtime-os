package net.corda.httprpc.server.impl.apigen.models

import net.corda.v5.httprpc.api.RpcOps
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
    val method: Method,
    val instance: RpcOps
)