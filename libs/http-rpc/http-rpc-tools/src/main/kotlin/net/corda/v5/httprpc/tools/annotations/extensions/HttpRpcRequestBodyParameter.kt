package net.corda.v5.httprpc.tools.annotations.extensions

import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import java.lang.reflect.Parameter

fun HttpRpcRequestBodyParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcRequestBodyParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name