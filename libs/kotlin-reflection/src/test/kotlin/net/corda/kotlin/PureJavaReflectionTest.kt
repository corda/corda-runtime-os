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
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties

@Timeout(5, unit = MINUTES)
class PureJavaReflectionTest {
    class ClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(PureJava::class)
            )
        }
    }

    class InterfaceProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(JavaExtraApi::class),
                Arguments.of(JavaApi::class)
            )
        }
    }

    class AllProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(PureJava::class),
                Arguments.of(JavaExtraApi::class),
                Arguments.of(JavaApi::class)
            )
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testClassDeclaredMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.declaredMemberProperties
        val cordaProperties = klazz.kotlinClass.declaredMemberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(InterfaceProvider::class)
    fun testInterfaceDeclaredMemberProperties(klazz: KClass<*>) {
        assertThat(klazz.declaredMemberProperties).isEmpty()
        assertThat(klazz.kotlinClass.declaredMemberProperties).isEmpty()
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(AllProvider::class)
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
    @ArgumentsSource(AllProvider::class)
    fun testMemberFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.memberFunctions
        val cordaFunctions = klazz.kotlinClass.memberFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testClassMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.memberProperties
        val cordaProperties = klazz.kotlinClass.memberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Properties")
    @ArgumentsSource(InterfaceProvider::class)
    fun testInterfaceMemberProperties(klazz: KClass<*>) {
        assertThat(klazz.memberProperties).isEmpty()
        assertThat(klazz.kotlinClass.memberProperties).isEmpty()
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Static Properties")
    @ArgumentsSource(AllProvider::class)
    fun testStaticProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.staticProperties
        val cordaProperties = klazz.kotlinClass.staticProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Static Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testStaticClassFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.staticFunctions
        val cordaFunctions = klazz.kotlinClass.staticFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Static Functions")
    @ArgumentsSource(InterfaceProvider::class)
    fun testStaticInterfaceFunctions(klazz: KClass<*>) {
        assertThat(klazz.staticFunctions).isEmpty()
        assertThat(klazz.kotlinClass.staticFunctions).isEmpty()
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Members")
    @ArgumentsSource(AllProvider::class)
    fun testMembers(klazz: KClass<*>) {
        val kotlinMembers = klazz.members
        val cordaMembers = klazz.kotlinClass.members
        assertThat(cordaMembers)
            .usingElementComparator(::compareKotlinCallables)
            .containsExactlyInAnyOrderElementsOf(kotlinMembers)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Static Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.functions
        val cordaFunctions = klazz.kotlinClass.functions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }
}
