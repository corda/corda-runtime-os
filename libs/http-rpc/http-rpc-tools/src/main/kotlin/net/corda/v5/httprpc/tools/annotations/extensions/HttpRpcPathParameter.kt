package net.corda.v5.httprpc.tools.annotations.extensions

import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import java.lang.reflect.Parameter

fun HttpRpcPathParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.toLowerCase()
fun HttpRpcPathParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.toLowerCase()
