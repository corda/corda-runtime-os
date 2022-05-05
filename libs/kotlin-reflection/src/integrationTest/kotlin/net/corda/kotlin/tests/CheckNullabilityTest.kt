package net.corda.kotlin.tests

import net.corda.kotlin.reflect.kotlinClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.TimeUnit.MINUTES
import java.util.Locale
import java.util.stream.Stream

@Timeout(5, unit = MINUTES)
class CheckNullabilityTest {
    class ExtendedKotlinApiPropertyProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("nativeLong", false),
                Arguments.of("nullableString", true),
                Arguments.of("nonNullableString", false),
                Arguments.of("neverNull", false),
                Arguments.of("listOfItems", false)
            )
        }
    }

    class KotlinBasePropertyProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("baseNullable", true),
                Arguments.of("baseNonNullable", false)
            )
        }
    }

    class MemberPropertyProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("nativeLong", false),
                Arguments.of("nullableString", true),
                Arguments.of("nonNullableString", false),
                Arguments.of("neverNull", false),
                Arguments.of("listOfItems", false),
                Arguments.of("baseNullable", true),
                Arguments.of("baseNonNullable", false),
                Arguments.of("protectedBaseNullable", true),
                Arguments.of("privateVar", false)
            )
        }
    }

    private fun String.capitalise() = replaceFirstChar { c ->
        if (c.isLowerCase()) {
            c.titlecase(Locale.getDefault())
        } else {
            c.toString()
        }
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("KotlinExample:ExtendedKotlinApi")
    @ArgumentsSource(ExtendedKotlinApiPropertyProvider::class)
    fun testKotlinExampleProperties(propertyName: String, isNullable: Boolean) {
        val property = KotlinExample::class.kotlinClass.declaredMemberProperties.find { it.name == propertyName }
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("KotlinExample:KotlinBase")
    @ArgumentsSource(KotlinBasePropertyProvider::class)
    fun testKotlinExampleInheritedProperties(propertyName: String, isNullable: Boolean) {
        assertNull(KotlinExample::class.kotlinClass.declaredMemberProperties.find { it.name == propertyName })
        val getter = KotlinExample::class.java.getMethod("get${propertyName.capitalise()}")
        val property = getter.declaringClass.kotlinClass.findPropertyForGetter(getter)
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("JavaExample:ExtendedKotlinApi")
    @ArgumentsSource(ExtendedKotlinApiPropertyProvider::class)
    fun testJavaExampleProperties(propertyName: String, isNullable: Boolean) {
        val property = JavaExample::class.kotlinClass.declaredMemberProperties.find { it.name == propertyName }
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("JavaExample:KotlinBase")
    @ArgumentsSource(KotlinBasePropertyProvider::class)
    fun testJavaExampleInheritedProperties(propertyName: String, isNullable: Boolean) {
        assertNull(JavaExample::class.kotlinClass.declaredMemberProperties.find { it.name == propertyName })
        val getter = JavaExample::class.java.getMethod("get${propertyName.capitalise()}")
        val property = getter.declaringClass.kotlinClass.findPropertyForGetter(getter)
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("KotlinExample Members")
    @ArgumentsSource(MemberPropertyProvider::class)
    fun testKotlinExampleMemberProperties(propertyName: String, isNullable: Boolean) {
        val property = KotlinExample::class.kotlinClass.memberProperties.find { it.name == propertyName }
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }

    @ParameterizedTest(name = "{displayName} => {0} nullability={1}")
    @DisplayName("JavaExample Members")
    @ArgumentsSource(MemberPropertyProvider::class)
    fun testJavaExampleMemberProperties(propertyName: String, isNullable: Boolean) {
        val property = JavaExample::class.kotlinClass.memberProperties.find { it.name == propertyName }
                ?: fail("Property $propertyName not found")
        assertEquals(isNullable, property.returnType.isMarkedNullable)
    }
}
