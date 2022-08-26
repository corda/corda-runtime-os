@file:JvmName("MethodUtils")
package net.corda.v5.base.stream

import java.lang.reflect.Method

fun Method.returnsDurableCursorBuilder() = DurableCursorBuilder::class.java.isAssignableFrom(returnType)

fun Method.isFiniteDurableStreamsMethod() =
    FiniteDurableCursorBuilder::class.java.isAssignableFrom(returnType)
