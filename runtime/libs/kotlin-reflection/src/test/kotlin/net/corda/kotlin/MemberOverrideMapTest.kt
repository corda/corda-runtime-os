package net.corda.kotlin

import net.corda.kotlin.reflect.types.MemberOverrideMap
import net.corda.kotlin.reflect.types.toSignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
class MemberOverrideMapTest {
    class ApiProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Arrays.stream(Api::class.java.methods)
                .map(Arguments::of)
        }
    }

    class ImplProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Arrays.stream(Impl::class.java.methods)
                .filter { method -> method.declaringClass === Impl::class.java }
                .map(Arguments::of)
        }
    }

    private val map = MemberOverrideMap<Method>()

    @ParameterizedTest
    @DisplayName("Class Implements Interface: {0}")
    @ArgumentsSource(ImplProvider::class)
    fun testMatchingImplByApi(impl: Method) {
        val implSignature = impl.toSignature()
        assertNull(map.put(implSignature, impl))
        assertEquals(impl, map[implSignature])
        assertEquals(1, map.size)

        @Suppress("SpreadOperator")
        val api = Api::class.java.getMethod(impl.name, *impl.parameterTypes)
        val apiSignature = api.toSignature()
        assertEquals(impl, map[apiSignature])
        assertEquals(impl, map.remove(apiSignature))
        assertEquals(0, map.size)
    }

    @ParameterizedTest
    @DisplayName("Class Overrides Intterface: {0}")
    @ArgumentsSource(ApiProvider::class)
    fun testMatchingApiByImpl(api: Method) {
        val apiSignature = api.toSignature()
        assertNull(map.put(apiSignature, api))
        assertEquals(api, map[apiSignature])
        assertEquals(1, map.size)

        @Suppress("SpreadOperator")
        val impl = Impl::class.java.getMethod(api.name, *api.parameterTypes)
        val implSignature = impl.toSignature()
        assertEquals(api, map.put(implSignature, impl))
        assertEquals(impl, map[apiSignature])
        assertEquals(impl, map[implSignature])
        assertEquals(1, map.size)

        assertEquals(impl, map.remove(implSignature))
        assertEquals(0, map.size)
    }

    @ParameterizedTest
    @DisplayName("Deleting By Key: {0}")
    @ArgumentsSource(ImplProvider::class)
    fun testDeletingByKey(impl: Method) {
        map[impl.toSignature()] = impl

        val keyIterator = map.keys.iterator()
        assertTrue(keyIterator.hasNext())
        assertEquals(impl.toSignature(), keyIterator.next())
        assertFalse(keyIterator.hasNext())

        keyIterator.remove()
        assertTrue(map.isEmpty())
    }

    @ParameterizedTest
    @DisplayName("Deleting By Key: {0}")
    @ArgumentsSource(ImplProvider::class)
    fun testDeletingByEntry(impl: Method) {
        map[impl.toSignature()] = impl

        val entryIterator = map.entries.iterator()
        assertTrue(entryIterator.hasNext())
        assertThat(entryIterator.next())
            .extracting("key", "value")
            .containsExactly(impl.toSignature(), impl)
        assertFalse(entryIterator.hasNext())

        entryIterator.remove()
        assertTrue(map.isEmpty())
    }

    interface Api {
        fun getData(obj: Any?): Any?
        var values: List<String>
        fun getPrimitive(): Int?
    }

    abstract class Impl(override var values: List<String>) : Api {
        abstract override fun getData(obj: Any?): String
        override fun getPrimitive(): Int {
            return 0
        }
    }
}
