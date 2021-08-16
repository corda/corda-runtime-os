package net.corda.kotlin

import net.corda.kotlin.reflect.kotlinClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberExtensionProperties
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties

@Timeout(5, unit = MINUTES)
class ReflectionTest {
    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Basic Class")
    @ValueSource(classes = [
        KotlinExample::class,
        JavaExample::class,
        ExtraApi::class,
        Base::class,
        AtomicInteger::class,
        String::class,
        Any::class
    ])
    fun testBasics(type: Class<*>) {
        val klass = type.kotlin
        val kotlinClass = type.kotlinClass

        assertEquals(klass.qualifiedName, kotlinClass.qualifiedName)
        assertEquals(klass.simpleName, kotlinClass.simpleName)
        assertEquals(klass.isAbstract, kotlinClass.isAbstract)
        assertEquals(klass.isFinal, kotlinClass.isFinal)
        assertEquals(klass.isOpen, kotlinClass.isOpen)
        assertEquals(klass.visibility, kotlinClass.visibility)
    }

    class ClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(JavaExtendsJavaWithKotlinApi::class),
                Arguments.of(JavaWithKotlinApi::class),
                Arguments.of(KotlinExample::class),
                Arguments.of(JavaExample::class),
                Arguments.of(LinkedHashMap::class),
                Arguments.of(AtomicInteger::class),
                Arguments.of(KotlinVersion::class),
                Arguments.of(ExtraApi::class),
                Arguments.of(JavaBase::class),
                Arguments.of(Base::class),
                Arguments.of(Api::class)
            )
        }
    }

    class KotlinClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(KotlinExample::class),
                Arguments.of(ExtraApi::class),
                Arguments.of(Base::class),
                Arguments.of(Api::class)
            )
        }
    }

    class JavaClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(JavaExample::class),
                Arguments.of(JavaBase::class)
            )
        }
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.declaredMemberProperties
        val cordaProperties = klazz.kotlinClass.declaredMemberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Kotlin Declared Member Extension Properties")
    @ArgumentsSource(KotlinClassProvider::class)
    fun testKotlinDeclaredMemberExtensionProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.declaredMemberExtensionProperties
        val cordaProperties = klazz.kotlinClass.declaredMemberExtensionProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Declared Member Extension Propertis")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaDeclaredMemberExtensionProperties(klazz: KClass<*>) {
        assertThat(klazz.declaredMemberExtensionProperties).isEmpty()
        assertThat(klazz.kotlinClass.declaredMemberExtensionProperties).isEmpty()
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
    @DisplayName("Kotlin Declared Member Extension Functions")
    @ArgumentsSource(KotlinClassProvider::class)
    fun testKotlinDeclaredMemberExtensionFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.declaredMemberExtensionFunctions
        val cordaFunctions = klazz.kotlinClass.declaredMemberExtensionFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Declared Member Extension Functions")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaDeclaredMemberExtensionFunctions(klazz: KClass<*>) {
        assertThat(klazz.declaredMemberExtensionFunctions).isEmpty()
        assertThat(klazz.kotlinClass.declaredMemberExtensionFunctions).isEmpty()
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Static Properties")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaStaticProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.staticProperties
        val cordaProperties = klazz.kotlinClass.staticProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Static Functions")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaStaticFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.staticFunctions
        val cordaFunctions = klazz.kotlinClass.staticFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Declared Members")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMembers(klazz: KClass<*>) {
        val kotlinMembers = klazz.declaredMembers
        val cordaMembers = klazz.kotlinClass.declaredMembers
        assertThat(cordaMembers)
            .usingElementComparator(::compareKotlinCallables)
            .containsExactlyInAnyOrderElementsOf(kotlinMembers)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testMemberProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.memberProperties
        val cordaProperties = klazz.kotlinClass.memberProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
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

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Kotlin Member Extension Properties")
    @ArgumentsSource(KotlinClassProvider::class)
    fun testKotlinMemberExtensionProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.memberExtensionProperties
        val cordaProperties = klazz.kotlinClass.memberExtensionProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Kotlin Member Extension Functions")
    @ArgumentsSource(KotlinClassProvider::class)
    fun testKotlinMemberExtensionFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.memberExtensionFunctions
        val cordaFunctions = klazz.kotlinClass.memberExtensionFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Member Extension Properties")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaMemberExtensionProperties(klazz: KClass<*>) {
        val kotlinProperties = klazz.memberExtensionProperties
        val cordaProperties = klazz.kotlinClass.memberExtensionProperties
        assertThat(cordaProperties)
            .usingElementComparator(::compareKotlinProperties)
            .containsExactlyInAnyOrderElementsOf(kotlinProperties)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Java Member Extension Functions")
    @ArgumentsSource(JavaClassProvider::class)
    fun testJavaMemberExtensionFunctions(klazz: KClass<*>) {
        val kotlinFunctions = klazz.memberExtensionFunctions
        val cordaFunctions = klazz.kotlinClass.memberExtensionFunctions
        assertThat(cordaFunctions)
            .usingElementComparator(::compareKotlinFunctions)
            .containsExactlyInAnyOrderElementsOf(kotlinFunctions)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Members")
    @ArgumentsSource(ClassProvider::class)
    fun testMembers(klazz: KClass<*>) {
        val kotlinMembers = klazz.members
        val cordaMembers = klazz.kotlinClass.members
        assertThat(cordaMembers)
            .usingElementComparator(::compareKotlinCallables)
            .containsExactlyInAnyOrderElementsOf(kotlinMembers)
            .isNotEmpty
    }

    @ParameterizedTest(name = "{displayName} => {0}")
    @DisplayName("Functions")
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
