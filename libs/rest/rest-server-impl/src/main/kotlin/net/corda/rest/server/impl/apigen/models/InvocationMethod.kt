package net.corda.rest.server.impl.apigen.models

import net.corda.rest.RestResource
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
    val method: Method,
    val instance: RestResource
)