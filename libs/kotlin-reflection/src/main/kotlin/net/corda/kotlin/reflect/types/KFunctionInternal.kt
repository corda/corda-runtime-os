package net.corda.kotlin.reflect.types

import java.lang.reflect.Method
import kotlin.reflect.KFunction

/**
 * Internal API for Kotlin functions.
 */
interface KFunctionInternal<T> : KFunction<T>, KInternal {
    val signature: MemberSignature?
    val javaMethod: Method?

    fun asFunctionFor(instanceClass: Class<*>, isExtension: Boolean): KFunctionInternal<T>
    fun withJavaMethod(method: Method): KFunctionInternal<T>
}
