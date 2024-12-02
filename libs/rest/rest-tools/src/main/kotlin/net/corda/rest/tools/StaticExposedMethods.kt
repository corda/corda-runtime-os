package net.corda.rest.tools

import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.ws.DuplexChannel
import java.lang.reflect.Method

private data class MethodDocs(val methodDescription: String, val responseDescription: String, val maxVersion: RestApiVersion)

/**
 * These methods are automatically exposed from the HTTP-RPC functionality as GET methods.
 * They are public methods, requiring no special authorization.
 *
 * Note: These are also exempt from sanity checks in HttpRpcClientProxyHandler.invoke(...).
 */
private val staticExposedGetMethods: Map<String, MethodDocs> =
    mapOf(
        "getProtocolVersion" to MethodDocs(
            "Returns the version of the endpoint",
            "An integer value specifying the version of the endpoint",
            RestApiVersion.C5_2
        )
    )
        .mapKeys { it.key.lowercase() }

fun Method.isStaticallyExposedGet(): Boolean {
    return staticExposedGetMethods.keys.contains(name.lowercase())
}

val Method.methodDescription: String
    get() {
        return staticExposedGetMethods[name.lowercase()]?.methodDescription ?: ""
    }

val Method.responseDescription: String
    get() {
        return staticExposedGetMethods[name.lowercase()]?.responseDescription ?: ""
    }

val Method.maxVersion: RestApiVersion
    get() {
        return staticExposedGetMethods[name.lowercase()]!!.maxVersion
    }

fun Class<*>.isDuplexChannel(): Boolean = (this == DuplexChannel::class.java)
