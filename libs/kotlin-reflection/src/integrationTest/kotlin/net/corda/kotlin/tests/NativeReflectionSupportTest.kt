package net.corda.kotlin.tests

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberExtensionFunctions
import kotlin.reflect.full.declaredMemberExtensionProperties
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

@Timeout(5, unit = MINUTES)
class NativeReflectionSupportTest {
    class NativeClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(Any::class),
                Arguments.of(LinkedHashMap::class)
            )
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(NativeReflectionSupportTest::class.java)

    private fun logInfo(obj: Any?) {
        logger.info("{}", obj)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Members")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinMembersAvailable(klazz: KClass<*>) {
        klazz.members.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Members")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinDeclaredMemberPropertiesAvailable(klazz: KClass<*>) {
        klazz.declaredMemberProperties.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Properties")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinDeclaredMemberExtensionPropertiesAvailable(klazz: KClass<*>) {
        klazz.declaredMemberExtensionProperties.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinDeclaredMemberFunctionsAvailable(klazz: KClass<*>) {
        klazz.declaredMemberFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Functions")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinDeclaredMemberExtensionFunctionsAvailable(klazz: KClass<*>) {
        klazz.declaredMemberExtensionFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Kotlin Static Functions")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinStaticFunctionsAvailable(klazz: KClass<*>) {
        klazz.staticFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Kotlin Static Properties")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinStaticPropertiesAvailable(klazz: KClass<*>) {
        klazz.staticProperties.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Kotlin Function for Constructor")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinFunctionForConstructorsAvailable(klazz: KClass<*>) {
        logger.info("{}", klazz.java.getConstructor().kotlinFunction)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Super Classes")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinSuperClassesAvailable(klazz: KClass<*>) {
        klazz.superclasses.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("All Super Classes")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinAllSuperClassesAvailable(klazz: KClass<*>) {
        klazz.allSuperclasses.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Super Types")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinSuperTypesAvailable(klazz: KClass<*>) {
        klazz.supertypes.map(KType::javaType).forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("All Super Types")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinAllSuperTypesAvailable(klazz: KClass<*>) {
        klazz.allSupertypes.map(KType::javaType).forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Constructors")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinConstructorsAvailable(klazz: KClass<*>) {
        klazz.constructors.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Primary Constructor")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinPrimaryConstructorAvailable(klazz: KClass<*>) {
        logger.info("{}", klazz.primaryConstructor)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Annotations")
    @ArgumentsSource(NativeClassProvider::class)
    fun testKotlinAnnotationsAvailable(klazz: KClass<*>) {
        klazz.annotations.forEach(::logInfo)
    }
}
