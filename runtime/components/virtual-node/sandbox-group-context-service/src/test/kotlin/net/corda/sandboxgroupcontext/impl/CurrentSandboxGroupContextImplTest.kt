package net.corda.sandboxgroupcontext.impl

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.impl.CurrentSandboxGroupContextImpl
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.concurrent.thread

class CurrentSandboxGroupContextImplTest {

    private val currentSandboxGroupContext = CurrentSandboxGroupContextImpl()

    @Test
    fun `set, get and remove the sandbox group context thread local`() {
        val sandboxGroupContextA = mock<SandboxGroupContext>()
        val sandboxGroupContextB = mock<SandboxGroupContext>()

        var failedAssertion: AssertionError? = null

        val threadA = thread(start = true) {
            try {
                currentSandboxGroupContext.set(sandboxGroupContextA)
                assertSame(sandboxGroupContextA, currentSandboxGroupContext.get())
                currentSandboxGroupContext.remove()
                assertThrows<IllegalStateException> { currentSandboxGroupContext.get() }
            } catch (e: AssertionError) {
                failedAssertion = e
            }
        }

        val threadB = thread(start = true) {
            try {
                currentSandboxGroupContext.set(sandboxGroupContextB)
                assertSame(sandboxGroupContextB, currentSandboxGroupContext.get())
                currentSandboxGroupContext.remove()
                assertThrows<IllegalStateException> { currentSandboxGroupContext.get() }
            } catch (e: AssertionError) {
                failedAssertion = e
            }
        }

        threadA.join()
        threadB.join()

        failedAssertion?.let { throw it }
    }
}