package net.corda.kotlin.tests

import net.corda.kotlin.reflect.kotlinClass
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

@Timeout(5, unit = MINUTES)
class KotlinClassReflectionTest {
    class ClassProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(Any::class),
                Arguments.of(LinkedHashMap::class),
                Arguments.of(KotlinExample::class),
                Arguments.of(JavaExample::class),
            )
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(KotlinClassReflectionTest::class.java)

    private fun logInfo(obj: Any?) {
        logger.info("{}", obj)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Members")
    @ArgumentsSource(ClassProvider::class)
    fun testMembers(klazz: KClass<*>) {
        klazz.kotlinClass.members.forEach { member ->
            logger.info("Member: $member")
            member.parameters.forEach { param ->
                logger.info("- $param")
            }
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Members")
    @ArgumentsSource(ClassProvider::class)
    fun testFunctions(klazz: KClass<*>) {
        klazz.kotlinClass.functions.forEach { function ->
            logger.info("Function: $function")
            function.parameters.forEach { param ->
                logger.info("- $param")
            }
        }
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Members")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMembers(klazz: KClass<*>) {
        klazz.kotlinClass.declaredMembers.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberProperties(klazz: KClass<*>) {
        klazz.kotlinClass.declaredMemberProperties.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberExtensionPropertiesAvailable(klazz: KClass<*>) {
        klazz.kotlinClass.declaredMemberExtensionProperties.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberFunctions(klazz: KClass<*>) {
        klazz.kotlinClass.declaredMemberFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Declared Member Extension Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testDeclaredMemberExtensionFunctions(klazz: KClass<*>) {
        klazz.kotlinClass.declaredMemberExtensionFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Static Functions")
    @ArgumentsSource(ClassProvider::class)
    fun testStaticFunctions(klazz: KClass<*>) {
        klazz.kotlinClass.staticFunctions.forEach(::logInfo)
    }

    @ParameterizedTest(name = "{displayName} -> {0}")
    @DisplayName("Static Properties")
    @ArgumentsSource(ClassProvider::class)
    fun testStaticProperties(klazz: KClass<*>) {
        klazz.kotlinClass.staticProperties.forEach(::logInfo)
    }
}
