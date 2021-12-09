package net.corda.sandboxgroupcontext

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.full.createType

class MutableSandboxGroupContextTest {
    /**
     * We do not want to allow [MutableSandboxGroupContext] to inherit from [SandboxGroupContext]
     * in case the user of the [MutableSandboxGroupContext] tries to capture it and use the "read-only" part elsewhere.
     *
     * We make no guarantees about the underlying implementation of either interface other than if you use
     * [net.corda.sandboxgroupcontext.MutableSandboxGroupContext.put],
     * the object can be retrieved by [net.corda.sandboxgroupcontext.SandboxGroupContext.get]
     */
    @Test
    fun `cannot cast MutableSandboxGroupContext to SandboxGroupContext`() {
        val sandboxGroupContextType: KType = SandboxGroupContext::class.createType()
        assertThat(MutableSandboxGroupContext::class.supertypes.contains<Any>(sandboxGroupContextType)).isFalse
    }
}
