package net.corda.httprpc.server.impl.apigen.models

import net.corda.httprpc.RpcOps
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
    val method: Method,
    val instance: RpcOps
)