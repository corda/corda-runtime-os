package net.corda.httprpc.server.impl.apigen.models

import net.corda.httprpc.RestResource
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
    val method: Method,
    val instance: RestResource
)