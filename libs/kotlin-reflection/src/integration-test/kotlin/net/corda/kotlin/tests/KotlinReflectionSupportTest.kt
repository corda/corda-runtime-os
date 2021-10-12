package net.corda.kotlin.tests

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
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
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.staticProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

@Timeout(5, unit = MINUTES)
class KotlinReflectionSupportTest {
    class ClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(KotlinExample::class),
                Arguments.of(JavaExample::class)
            )
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(KotlinReflectionSupportTest::class.java)

    private fun logInfo(obj: Any?) {
        logger.info("{}", obj)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Members")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinMembersBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.members
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Members")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinDeclaredMembersBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.declaredMembers
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinDeclaredMemberPropertiesBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.declaredMemberProperties
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinDeclaredMemberExtensionPropertiesBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.declaredMemberExtensionProperties
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinDeclaredMemberFunctionsBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.declaredMemberFunctions
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinDeclaredMemberExtensionFunctionsBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.declaredMemberExtensionFunctions
        }
    }

    @Test
    fun testKotlinStaticFunctionsBroken() {
        // Only Java classes have static functions.
        assertThrows<IllegalStateException> {
            JavaExample::class.staticFunctions
        }
    }

    @Test
    fun testKotlinStaticPropertiesBroken() {
        // Only Java classes have static properties.
        assertThrows<IllegalStateException> {
            JavaExample::class.staticProperties
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Kotlin Function for Method")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinFunctionBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.java.getMethod("getNullableString").kotlinFunction
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Kotlin Function for Constructor")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinFunctionForConstructorsAvailable(klazz: KClass<*>) {
        klazz.java.getConstructor().kotlinFunction
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Super Classes")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinSuperClassesAvailable(klazz: KClass<*>) {
        klazz.superclasses.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("All Super Classes")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinAllSuperClassesAvailable(klazz: KClass<*>) {
        klazz.allSuperclasses.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Super Types")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinSuperTypesAvailable(klazz: KClass<*>) {
        klazz.supertypes.map(KType::javaType).forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("All Super Types")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinAllSuperTypesAvailable(klazz: KClass<*>) {
        klazz.allSupertypes.map(KType::javaType).forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Constructors")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinConstructorsAvailable(klazz: KClass<*>) {
        klazz.constructors.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Primary Constructor")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinPrimaryConstructorAvailable(klazz: KClass<*>) {
        logger.info("{}", klazz.primaryConstructor)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Annotations")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinAnnotationsAvailable(klazz: KClass<*>) {
        klazz.annotations.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Sealed Subclasses")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinSealedSubclassesAvailable(klazz: KClass<*>) {
        klazz.sealedSubclasses.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Nested Classes")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinNestedClassesBroken(klazz: KClass<*>) {
        assertThrows<IllegalStateException> {
            klazz.nestedClasses
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Type Parameters")
    @ArgumentsSource(ClassProvider::class)
    fun testKotlinTypeParametersAvailable(klazz: KClass<*>) {
        klazz.typeParameters.forEach(::logInfo)
    }
}
