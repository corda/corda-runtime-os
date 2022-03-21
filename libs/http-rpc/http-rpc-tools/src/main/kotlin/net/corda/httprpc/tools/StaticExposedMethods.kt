package net.corda.httprpc.tools

import java.lang.reflect.Method

/**
 * These methods are automatically exposed from the HTTP-RPC functionality as GET methods.
 * They are public methods, requiring no special authorization.
 *
 * Note: These are also exempt from sanity checks in HttpRpcClientProxyHandler.invoke(...).
 */
private val staticExposedGetMethods: Set<String> = listOf("getProtocolVersion").map { it.toLowerCase() }.toSet()

fun Method.isStaticallyExposedGet(): Boolean {
    return staticExposedGetMethods.contains(name.toLowerCase())
}
