package net.corda.httprpc.tools.annotations.extensions

import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import java.lang.reflect.Method

fun HttpRpcPOST.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcPOST.path(annotated: Method): String = (this.path.takeIf { it.isNotBlank() } ?: annotated.name).toLowerCase()

fun HttpRpcGET.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcGET.path(annotated: Method): String = (this.path.takeIf { it.isNotBlank() } ?: annotated.name).toLowerCase()