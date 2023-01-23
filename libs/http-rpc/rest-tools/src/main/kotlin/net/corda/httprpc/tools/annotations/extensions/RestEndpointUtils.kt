package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.tools.isStaticallyExposedGet
import java.lang.reflect.Method

fun HttpRpcPOST.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcPOST.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpRpcPUT.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcPUT.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpRpcGET.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcGET.path(annotated: Method): String? {
    return if (annotated.isStaticallyExposedGet()) {
        annotated.name.lowercase()
    } else {
        this.path.takeIf { it.isNotBlank() }?.lowercase()
    }
}

fun HttpRpcDELETE.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcDELETE.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpRpcWS.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcWS.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()
