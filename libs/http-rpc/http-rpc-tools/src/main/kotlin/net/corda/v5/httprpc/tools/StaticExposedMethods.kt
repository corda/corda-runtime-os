package net.corda.v5.httprpc.tools

/**
 * These methods are automatically exposed from the HTTP-RPC functionality as GET methods.
 * They are public methods, requiring no special authorization.
 *
 * Note: These are also exempt from sanity checks in HttpRpcClientProxyHandler.invoke(...).
 */
val staticExposedGetMethods = setOf("getProtocolVersion")
