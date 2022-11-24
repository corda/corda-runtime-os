@file:JvmName("MethodUtils")
package net.corda.httprpc.durablestream.api

import java.lang.reflect.Method

fun Method.returnsDurableCursorBuilder() = DurableCursorBuilder::class.java.isAssignableFrom(returnType)

fun Method.isFiniteDurableStreamsMethod() =
    FiniteDurableCursorBuilder::class.java.isAssignableFrom(returnType)
