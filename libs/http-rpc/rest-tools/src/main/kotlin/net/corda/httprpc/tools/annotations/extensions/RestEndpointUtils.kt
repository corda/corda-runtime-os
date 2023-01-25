package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.HttpWS
import net.corda.httprpc.tools.isStaticallyExposedGet
import java.lang.reflect.Method

fun HttpPOST.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpPOST.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpPUT.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpPUT.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpGET.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpGET.path(annotated: Method): String? {
    return if (annotated.isStaticallyExposedGet()) {
        annotated.name.lowercase()
    } else {
        this.path.takeIf { it.isNotBlank() }?.lowercase()
    }
}

fun HttpDELETE.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpDELETE.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()

fun HttpWS.title(annotated: Method): String = this.title.takeIf { it.isNotBlank() } ?: annotated.name
fun HttpWS.path(): String? = this.path.takeIf { it.isNotBlank() }?.lowercase()
