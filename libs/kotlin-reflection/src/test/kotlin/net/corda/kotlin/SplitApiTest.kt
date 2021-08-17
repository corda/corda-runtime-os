package net.corda.kotlin

import net.corda.kotlin.reflect.kotlinClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaSetter

@Timeout(5, unit = MINUTES)
class SplitApiTest {
    class SplitClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(SplitApi::class),
                Arguments.of(JavaSplitParent::class),
                Arguments.of(JavaSplitChild::class)
            )
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(SplitClassProvider::class)
    fun testDeclaredMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.declaredMemberProperties
        val cordaProperties = klazz.kotlinClass.declaredMemberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .hasSameSizeAs(kotlinProperties)
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(SplitClassProvider::class)
    fun testDeclaredMemberFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.declaredMemberFunctions
        val cordaFunctions = klazz.kotlinClass.declaredMemberFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .hasSameSizeAs(kotlinFunctions)
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Properties")
    @ArgumentsSource(SplitClassProvider::class)
    fun testMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.memberProperties
        val cordaProperties = klazz.kotlinClass.memberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .hasSameSizeAs(kotlinProperties)
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Functions")
    @ArgumentsSource(SplitClassProvider::class)
    fun testMemberFunctions(klazz: KClass<*>) {
        assumeTrue(klazz != JavaSplitChild::class,
            "Kotlin Reflection includes spurious member function JavaSplitChild.getThirdApi()")
        val kotlinFunctions = klazz.memberFunctions
        val cordaFunctions = klazz.kotlinClass.memberFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Show Members")
    @ArgumentsSource(SplitClassProvider::class)
    fun showMembers(klazz: KClass<*>) {
        println(">> MEMBERS")
        klazz.members.forEach(::println)
        println(">> MEMBER FUNCTIONS")
        klazz.memberFunctions.forEach {
            println(it)
            println("- isAbstract: ${it.isAbstract}")
            println("- isFinal: ${it.isFinal}")
            println("- isOpen: ${it.isOpen}")
            println("- javaMethod: ${it.javaMethod}")
        }
        println(">> MEMBER PROPERTIES")
        klazz.memberProperties.forEach {
            println(it)
            println("- isAbstract: ${it.isAbstract}")
            println("- isFinal: ${it.isFinal}")
            println("- isOpen: ${it.isOpen}")
            println("- javaField: ${it.javaField}")
            println("- javaGetter: ${it.javaGetter}")
            if (it is KMutableProperty<*>) {
                println("- javaSetter: ${it.javaSetter}")
            }
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Show Declared Members")
    @ArgumentsSource(SplitClassProvider::class)
    fun showDeclaredMembers(klazz: KClass<*>) {
        println(">> DECLARED MEMBERS")
        klazz.declaredMembers.forEach(::println)
        println(">> DECLARED MEMBER FUNCTIONS")
        klazz.declaredMemberFunctions.forEach {
            println(it)
            println("- isAbstract: ${it.isAbstract}")
            println("- isFinal: ${it.isFinal}")
            println("- isOpen: ${it.isOpen}")
            println("- javaMethod: ${it.javaMethod}")
        }
        println(">> DECLARED MEMBER PROPERTIES")
        klazz.declaredMemberProperties.forEach {
            println(it)
            println("- isAbstract: ${it.isAbstract}")
            println("- isFinal: ${it.isFinal}")
            println("- isOpen: ${it.isOpen}")
            println("- javaField: ${it.javaField}")
            println("- javaGetter: ${it.javaGetter}")
            if (it is KMutableProperty<*>) {
                println("- javaSetter: ${it.javaSetter}")
            }
        }
    }
}
