@file:JvmName("Helpers")
package net.corda.kotlin

import net.corda.kotlin.reflect.kotlinJavaField
import net.corda.kotlin.reflect.kotlinJavaGetter
import net.corda.kotlin.reflect.kotlinJavaMethod
import net.corda.kotlin.reflect.kotlinJavaSetter
import net.corda.kotlin.reflect.types.KInternal
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.function.Executable
import java.util.function.Function
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2

fun compareKotlinCallables(call1: KCallable<*>, call2: KCallable<*>): Int {
    return when {
        call1 is KFunction<*> && call2 is KFunction<*> -> compareKotlinFunctions(call1, call2)
        call1 is KProperty<*> && call2 is KProperty<*> -> compareKotlinProperties(call1, call2)
        call1 is KFunction<*> && call2 is KProperty<*> -> -1
        else -> 1
    }
}

fun compareKotlinFunctions(func1: KFunction<*>, func2: KFunction<*>)
    = KFunctionComparator().compare(func1, func2)

fun compareKotlinProperties(prop1: KProperty<*>, prop2: KProperty<*>)
    = KPropertyComparator().compare(prop1, prop2)

private open class KCallableComparator<T : KCallable<*>> : Comparator<T> {
    override fun compare(call1: T, call2: T): Int {
        return compareBy(KCallable<*>::name)
            .thenComparing(KCallable<*>::parameters, ::compareAllParameters)
            .compare(call1, call2)
    }

    private fun compareAllParameters(params1: List<KParameter>, params2: List<KParameter>): Int {
        return when (val byCount = params1.size - params2.size) {
            0 -> {
                val comparator = compareBy(KParameter::kind)
                    .thenBy(KParameter::name)
                    .thenBy(KParameter::index)
                    .thenBy { param -> param.type.isMarkedNullable }
                    .thenBy { param ->
                        when (param.kind) {
                            // Our instance parameter types are currently wrong
                            // for [KmNativeType] objects.
                            INSTANCE -> "<FIXME>"
                            else -> param.type.classifier.toString()
                        }
                    }
                var result = 0
                for (idx in params1.indices) {
                    result = comparator.compare(params1[idx], params2[idx])
                    if (result != 0) {
                        break
                    }
                }
                result
            }
            else -> byCount
        }
    }

    private fun getTypeOfInstanceParameter(call: KCallable<*>): String {
        val parameters = call.parameters
        return if (parameters.isEmpty()) {
            ""
        } else {
            val parameter = parameters[0]
            if (parameter.kind == INSTANCE) {
                parameter.type.classifier.toString()
            } else {
                ""
            }
        }
    }

    protected fun assertCallablesEqual(arg1: T, arg2: T, tests: MutableList<Pair<String, Function<T, out Any?>>>) {
        val message: String
        val expected: T
        val actual: T
        when {
            arg1 is KInternal -> {
                actual = arg1
                expected = arg2
                message = actual.name
                // Check we set our instance parameter the same as Kotlin does.
                tests += "instance parameter type" to Function(::getTypeOfInstanceParameter)
            }
            arg2 is KInternal -> {
                actual = arg2
                expected = arg1
                message = actual.name
                // Check we set our instance parameter the same as Kotlin does.
                tests += "instance parameter type" to Function(::getTypeOfInstanceParameter)
            }
            else -> {
                actual = arg1
                expected = arg2
                message = "Cannot tell actual from expected?!"
                // We are most likely validating a [KmNativeType] object created for
                // a java.* or kotlin.* class such as [Any]. The instance parameter
                // type for such objects will currently still be wrong.
            }
        }
        assertAll(message, tests.map { test ->
            Executable {
                try {
                    assertEquals(test.second.apply(expected), test.second.apply(actual), test.first)
                } catch(_: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
                }
            }
        })
    }
}

private class KPropertyComparator : KCallableComparator<KProperty<*>>() {
    override fun compare(call1: KProperty<*>, call2: KProperty<*>): Int {
        val result = super.compare(call1, call2)
        if (result == 0) {
            val tests = mutableListOf<Pair<String, Function<out KProperty<*>, Any?>>>(
                "javaField" to Function(KProperty<*>::kotlinJavaField),
                "javaGetter" to Function(KProperty<*>::kotlinJavaGetter),
                "isFinal" to Function(KProperty<*>::isFinal),
                "isConst" to Function(KProperty<*>::isConst),
                "isLateInit" to Function(KProperty<*>::isLateinit),
                "isSuspend" to Function(KProperty<*>::isSuspend),
                "visibility" to Function(KProperty<*>::visibility),
                "is mutable" to Function { it is KMutableProperty<*> },
                "is static" to Function { it is KProperty0<*> },
                "is member" to Function { it is KProperty1<*, *> },
                "is member extension" to Function { it is KProperty2<*, *, *> }
            )

            if (call1 !is KProperty2<*, *, *> && call2 !is KProperty2<*, *, *>) {
                tests += listOf(
                    // Kotlin Reflection gets these wrong for member extensions in Java.
                    "isAbstract" to Function(KProperty<*>::isAbstract),
                    "isOpen" to Function(KProperty<*>::isOpen)
                )
            }
            if (call1 is KMutableProperty<*> && call2 is KMutableProperty<*>) {
                tests += "javaSetter" to Function(KMutableProperty<*>::kotlinJavaSetter)
            }

            @Suppress("unchecked_cast")
            assertCallablesEqual(call1, call2, tests as MutableList<Pair<String, Function<KProperty<*>, out Any?>>>)
        }
        return result
    }
}

private class KFunctionComparator : KCallableComparator<KFunction<*>>() {
    override fun compare(call1: KFunction<*>, call2: KFunction<*>): Int {
        val result = super.compare(call1, call2)
        if (result == 0) {
            assertCallablesEqual(call1, call2, mutableListOf(
                "javaMethod" to Function(KFunction<*>::kotlinJavaMethod),
                // Kotlin Reflection gets these wrong for member extensions in Java.
                //"isAbstract" to Function(KFunction<*>::isAbstract),
                //"isOpen" to Function(KFunction<*>::isOpen)
                "isFinal" to Function(KFunction<*>::isFinal),
                "isSuspend" to Function(KFunction<*>::isSuspend),
                "isExternal" to Function(KFunction<*>::isExternal),
                "isInfix" to Function(KFunction<*>::isInfix),
                "isInline" to Function(KFunction<*>::isInline),
                "isOperator" to Function(KFunction<*>::isOperator),
                "visibility" to Function(KFunction<*>::visibility)
            ))
        }
        return result
    }
}
