package net.corda.httprpc.server.apigen.processing

import net.corda.httprpc.server.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.server.stream.DurableStreamContext
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.security.auth.login.FailedLoginException

internal class MethodInvokerTest {
    @Test
    fun `invoke durableStreamsMethodInvoker withNoArgs throws`() {
        val invoker = DurableStreamsMethodInvoker(mock(), mock())

        assertThatThrownBy { invoker.invokeDurableStreamMethod() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Method returning Durable Streams was invoked without arguments.")
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withoutContextArg throws`() {
        val invoker = DurableStreamsMethodInvoker(mock(), mock())

        assertThatThrownBy { invoker.invokeDurableStreamMethod("test") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withMoreThan1ContextArg throws`() {
        val invoker = DurableStreamsMethodInvoker(mock(), mock())

        assertThatThrownBy { invoker.invokeDurableStreamMethod(DurableStreamContext(1, 1), DurableStreamContext(1, 1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withContextNotSet throws`() {
        val invoker = DurableStreamsMethodInvoker(mock(), mock())
        CURRENT_RPC_CONTEXT.remove()

        assertThatThrownBy { invoker.invokeDurableStreamMethod(DurableStreamContext(1L, 1)) }
            .isInstanceOf(FailedLoginException::class.java)
            .hasMessage("Missing authentication context.")
    }
}