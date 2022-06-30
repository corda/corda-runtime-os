package net.corda.httprpc.tools

import net.corda.httprpc.ws.DuplexChannel
import java.lang.reflect.Method

/**
 * These methods are automatically exposed from the HTTP-RPC functionality as GET methods.
 * They are public methods, requiring no special authorization.
 *
 * Note: These are also exempt from sanity checks in HttpRpcClientProxyHandler.invoke(...).
 */
private val staticExposedGetMethods: Set<String> = listOf("getProtocolVersion").map { it.lowercase() }.toSet()

fun Method.isStaticallyExposedGet(): Boolean {
    return staticExposedGetMethods.contains(name.lowercase())
}

fun Method.isDuplexRoute(): Boolean {
    return returnType == DuplexChannel::class.java
}