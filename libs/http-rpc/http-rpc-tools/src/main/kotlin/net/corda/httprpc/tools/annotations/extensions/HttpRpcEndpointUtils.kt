package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.tools.staticExposedGetMethods
import java.lang.reflect.Method

fun HttpRpcPOST.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcPOST.path(): String? = this.path.takeIf { it.isNotBlank() }?.toLowerCase()

fun HttpRpcGET.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpRpcGET.path(annotated: Method): String? {
    return if (staticExposedGetMethods.any { it.equals(annotated.name, true) }) {
        annotated.name.toLowerCase()
    } else {
        this.path.takeIf { it.isNotBlank() }?.toLowerCase()
    }
}