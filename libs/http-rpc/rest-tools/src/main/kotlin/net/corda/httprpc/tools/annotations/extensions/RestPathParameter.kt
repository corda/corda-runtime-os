package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcPathParameter
import java.lang.reflect.Parameter

fun HttpRpcPathParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.lowercase()
fun HttpRpcPathParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.lowercase()
