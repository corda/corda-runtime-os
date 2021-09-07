package net.corda.internal.serialization

import net.corda.v5.base.concurrent.getOrThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun <T> withSingleThreadExecutor(callable: ExecutorService.() -> T) = Executors.newSingleThreadExecutor().run {
    try {
        supplyAsync({}, this).getOrThrow() // Start the thread.
        callable()
    } finally {
        shutdown() // Do not change to shutdownNow, tests use this method to assert the executor has no more tasks.
        while (!awaitTermination(1, TimeUnit.SECONDS)) {
            // Try forever. Do not give up, tests use this method to assert the executor has no more tasks.
        }
    }
}

class VerifyNoMoreInteractions : AfterEachCallback {
    override fun afterEach(context: ExtensionContext?) {
        verifyNoMoreInteractions(ToggleFieldTest.log)
    }
}
@ExtendWith(VerifyNoMoreInteractions::class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ToggleFieldTest {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val companionName = javaClass.name

        private fun <T> globalThreadCreationMethod(task: () -> T) = task()

        val log = mock<Logger>()
    }

    private fun <T> inheritableThreadLocalToggleField() = InheritableThreadLocalToggleField<T>("inheritable", log) { stack ->
        stack.fold(false) { isAGlobalThreadBeingCreated, e ->
            isAGlobalThreadBeingCreated || (e.className == companionName && e.methodName == "globalThreadCreationMethod")
        }
    }

    @Test
    fun `toggle is enforced`() {
        listOf(
            SimpleToggleField<String>("simple"),
            ThreadLocalToggleField<String>("local"),
            inheritableThreadLocalToggleField()
        ).forEach { field ->
            assertNull(field.get())
            assertThatThrownBy { field.set(null) }.isInstanceOf(IllegalStateException::class.java)
            field.set("hello")
            assertEquals("hello", field.get())
            assertThatThrownBy { field.set("world") }.isInstanceOf(IllegalStateException::class.java)
            assertEquals("hello", field.get())
            assertThatThrownBy { field.set("hello") }.isInstanceOf(IllegalStateException::class.java)
            field.set(null)
            assertNull(field.get())
        }
    }

    @Test
    fun `write-at-most-once field works`() {
        val field = SimpleToggleField<String>("field", true)
        assertNull(field.get())
        assertThatThrownBy { field.set(null) }.isInstanceOf(IllegalStateException::class.java)
        field.set("finalValue")
        assertEquals("finalValue", field.get())
        listOf("otherValue", "finalValue", null).forEach { value ->
            assertThatThrownBy { field.set(value) }.isInstanceOf(IllegalStateException::class.java)
            assertEquals("finalValue", field.get())
        }
    }

    @Test
    fun `thread local works`() {
        val field = ThreadLocalToggleField<String>("field")
        assertNull(field.get())
        field.set("hello")
        assertEquals("hello", field.get())
        withSingleThreadExecutor {
            assertNull(supplyAsync(field::get, this).getOrThrow())
        }
        field.set(null)
        assertNull(field.get())
    }

    @Test
    fun `inheritable thread local works`() {
        val field = inheritableThreadLocalToggleField<String>()
        assertNull(field.get())
        field.set("hello")
        assertEquals("hello", field.get())
        withSingleThreadExecutor {
            assertEquals("hello", supplyAsync(field::get, this).getOrThrow())
        }
        field.set(null)
        assertNull(field.get())
    }

    @Test
    fun `existing threads do not inherit`() {
        val field = inheritableThreadLocalToggleField<String>()
        withSingleThreadExecutor {
            field.set("hello")
            assertEquals("hello", field.get())
            assertNull(supplyAsync(field::get, this).getOrThrow())
        }
    }

    @Test
    fun `inherited values are poisoned on clear`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", supplyAsync(field::get, this).getOrThrow())
            val threadName = supplyAsync({ Thread.currentThread().name }, this).getOrThrow()
            listOf(null, "world").forEach { value ->
                field.set(value)
                assertEquals(value, field.get())
                val future = supplyAsync(field::get, this)
                assertThatThrownBy { future.getOrThrow() }
                    .isInstanceOf(ThreadLeakException::class.java)
                    .hasMessageContaining(threadName)
                    .hasMessageContaining("hello")
            }
        }
        withSingleThreadExecutor {
            assertEquals("world", supplyAsync(field::get, this).getOrThrow())
        }
    }

    /** We log a warning rather than failing-fast as the new thread may be an undetected global. */
    @Test
    fun `leaked thread propagates holder to non-global thread, with warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", supplyAsync(field::get, this).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            supplyAsync(
                {
                    val leakedThreadName = Thread.currentThread().name
                    verifyNoMoreInteractions(log)
                    withSingleThreadExecutor {
                        // If ThreadLeakException is seen in practice, these warnings form a trail of where the holder has been:
                        verify(log).warn(argThat { contains(leakedThreadName) && contains("hello") })
                        val newThreadName = supplyAsync({ Thread.currentThread().name }, this).getOrThrow()
                        val future = supplyAsync(field::get, this)
                        assertThatThrownBy { future.getOrThrow() }
                            .isInstanceOf(ThreadLeakException::class.java)
                            .hasMessageContaining(newThreadName)
                            .hasMessageContaining("hello")
                        supplyAsync(
                            {
                                verifyNoMoreInteractions(log)
                                withSingleThreadExecutor {
                                    verify(log).warn(argThat { contains(newThreadName) && contains("hello") })
                                }
                            },
                            this
                        ).getOrThrow()
                    }
                },
                this
            ).getOrThrow()
        }
    }

    @Test
    fun `leaked thread does not propagate holder to global thread, with warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            assertEquals("hello", supplyAsync(field::get, this).getOrThrow())
            field.set(null) // The executor thread is now considered leaked.
            supplyAsync(
                {
                    val leakedThreadName = Thread.currentThread().name
                    globalThreadCreationMethod {
                        verifyNoMoreInteractions(log)
                        withSingleThreadExecutor {
                            verify(log).warn(argThat { contains(leakedThreadName) && contains("hello") })
                            // In practice the new thread is for example a static thread we can't get rid of:
                            assertNull(supplyAsync(field::get, this).getOrThrow())
                        }
                    }
                },
                this
            ).getOrThrow()
        }
    }

    @Test
    fun `non-leaked thread does not propagate holder to global thread, without warning`() {
        val field = inheritableThreadLocalToggleField<String>()
        field.set("hello")
        withSingleThreadExecutor {
            supplyAsync(
                {
                    assertEquals("hello", field.get())
                    globalThreadCreationMethod {
                        withSingleThreadExecutor {
                            assertNull(supplyAsync(field::get, this).getOrThrow())
                        }
                    }
                },
                this
            ).getOrThrow()
        }
    }
}
