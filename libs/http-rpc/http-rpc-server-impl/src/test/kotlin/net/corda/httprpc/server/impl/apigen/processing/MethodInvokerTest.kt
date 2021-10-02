package net.corda.httprpc.server.impl.apigen.processing

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.server.impl.rpcops.TestHealthCheckAPI
import net.corda.httprpc.server.impl.apigen.models.InvocationMethod
import net.corda.httprpc.server.impl.rpcops.impl.TestHealthCheckAPIImpl
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.security.auth.login.FailedLoginException
import kotlin.reflect.jvm.javaMethod

internal class MethodInvokerTest {

    private val invoker = DurableStreamsMethodInvoker(
        InvocationMethod(
            TestHealthCheckAPI::bodyPlayground.javaMethod!!,
            TestHealthCheckAPIImpl()
        ), mock()
    )

    @Test
    fun `invoke durableStreamsMethodInvoker withNoArgs throws`() {
        assertThatThrownBy { invoker.invokeDurableStreamMethod() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Method returning Durable Streams was invoked without arguments.")
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withoutContextArg throws`() {
        assertThatThrownBy { invoker.invokeDurableStreamMethod("test") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withMoreThan1ContextArg throws`() {
        assertThatThrownBy {
            invoker.invokeDurableStreamMethod(DurableStreamContext(1, 1), DurableStreamContext(1, 1))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `invoke durableStreamsMethodInvoker withContextNotSet throws`() {
        CURRENT_RPC_CONTEXT.remove()
        assertThatThrownBy { invoker.invokeDurableStreamMethod(DurableStreamContext(1L, 1)) }
            .isInstanceOf(FailedLoginException::class.java)
            .hasMessage("Missing authentication context.")
    }
}