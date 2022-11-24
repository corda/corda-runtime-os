package net.corda.httprpc.tools

import net.corda.httprpc.ws.DuplexChannel
import java.lang.reflect.Method

/**
 * These methods are automatically exposed from the HTTP-RPC functionality as GET methods.
 * They are public methods, requiring no special authorization.
 *
 * Note: These are also exempt from sanity checks in HttpRpcClientProxyHandler.invoke(...).
 */
private val staticExposedGetMethods: Map<String, String> =
    mapOf("getProtocolVersion" to "An integer value specifying the version of the endpoint")
        .mapKeys { it.key.lowercase() }

fun Method.isStaticallyExposedGet(): Boolean {
    return staticExposedGetMethods.keys.contains(name.lowercase())
}

val Method.responseDescription: String
    get() {
        return staticExposedGetMethods[name.lowercase()] ?: ""
    }

fun Class<*>.isDuplexChannel(): Boolean = (this == DuplexChannel::class.java)