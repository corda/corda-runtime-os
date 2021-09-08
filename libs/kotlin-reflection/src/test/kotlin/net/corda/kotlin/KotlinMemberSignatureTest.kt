package net.corda.kotlin

import net.corda.kotlin.reflect.types.toSignature
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.lang.reflect.Method
import java.util.Arrays
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream

@Timeout(5, unit = MINUTES)
class KotlinMemberSignatureTest {
    class MethodProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Arrays.stream(ExampleApi::class.java.methods).map(Arguments::of)
        }
    }

    @ParameterizedTest
    @DisplayName("Kotlin Interface Method: {0}")
    @ArgumentsSource(MethodProvider::class)
    fun testInterfaceForClass(method: Method) {
        val api = method.toSignature()
        @Suppress("SpreadOperator")
        val impl = ExampleImpl::class.java.getMethod(api.name, *api.parameterTypes).toSignature()
        assertAll(
            method.name,
            { assertTrue(api.isAssignableFrom(impl), "API not assignable from Impl") },
            {
                assertTrue(
                    !impl.isAssignableFrom(api) || impl == api,
                    "Impl incorrectly assignable from API"
                )
            }
        )
    }

    interface ExampleApi {
        fun getData(obj: Any?): Any?
        var values: List<String>
    }

    class ExampleImpl(override var values: List<String>) : ExampleApi {
        override fun getData(obj: Any?): String {
            return obj.toString()
        }
    }
}
