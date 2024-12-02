package net.corda.rest.server.impl.apigen.models

import net.corda.rest.RestResource
import net.corda.rest.response.ResponseEntity
import java.lang.reflect.Method

@Suppress("SpreadOperator")
data class InvocationMethod(
    val method: Method,
    val instance: RestResource,
    val transform: ((Any?) -> ResponseEntity<Any?>)? = null
)
