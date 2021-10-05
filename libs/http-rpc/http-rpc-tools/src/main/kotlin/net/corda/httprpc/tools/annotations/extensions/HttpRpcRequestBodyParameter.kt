package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import java.lang.reflect.Parameter

fun HttpRpcRequestBodyParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcRequestBodyParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name