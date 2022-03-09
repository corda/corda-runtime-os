package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import java.lang.reflect.Method

fun HttpRpcPOST.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcPOST.path(annotated: Method): String = (this.path.takeIf { it.isNotBlank() } ?: annotated.name).toLowerCase()

fun HttpRpcGET.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcGET.path(annotated: Method): String = (this.path.takeIf { it.isNotBlank() } ?: annotated.name).toLowerCase()