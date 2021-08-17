package net.corda.kotlin

import net.corda.kotlin.reflect.kotlinClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberFunctions


@Timeout(5, unit = MINUTES)
class KotlinClassHierarchyTest {

    class ClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(ImplA::class),
                Arguments.of(ImplB::class),
                Arguments.of(ImplC::class),
                Arguments.of(ImplD::class),
                Arguments.of(Api::class)
            )
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.declaredMemberFunctions
        val cordaFunctions = klazz.kotlinClass.declaredMemberFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testMemberFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.memberFunctions
        val cordaFunctions = klazz.kotlinClass.memberFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    open class ImplA {
        open fun getThingy(): Any? = null
    }

    open class ImplB : ImplA() {
        override fun getThingy(): List<Any> = emptyList()
    }

    open class ImplC : ImplB() {
        override fun getThingy() = ArrayList<String>()
    }

    // The KotlinClass comparator is by assignability
    // first, and fully qualified class name second.
    @Suppress("unused")
    interface ZApi {
        fun getThingy(): Any?
    }

    @Suppress("unused")
    class ImplD : ImplC(), ZApi {
        fun somethingElse() {}
    }
}
