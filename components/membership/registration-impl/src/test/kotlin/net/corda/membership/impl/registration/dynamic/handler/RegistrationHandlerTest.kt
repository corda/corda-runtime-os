package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.CompletedCommandMetadata
import net.corda.data.membership.state.RegistrationState
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Callable

class RegistrationHandlerTest {

    private class MyCommand

    private val mockCommand = mock<RegistrationCommand> {
        on { command } doReturn mock<MyCommand>()
    }
    private val mockRecord = mock<Record<String, RegistrationCommand>> {
        on { key } doReturn "abc-123"
        on { value } doReturn mockCommand
    }

    private val resultFactory = mock<Callable<RegistrationHandlerResult>>()

    private val testImpl = object : RegistrationHandler<MyCommand> {
        override fun getOwnerHoldingId(state: RegistrationState?, command: MyCommand): HoldingIdentity? = null

        override fun invoke(state: RegistrationState?, key: String, command: MyCommand): RegistrationHandlerResult {
            return resultFactory.call()
        }

        override val commandType: Class<MyCommand> = MyCommand::class.java
    }

    @Test
    fun `command metadata is not added if state is null`() {
        whenever(resultFactory.call()).doReturn(RegistrationHandlerResult(null, emptyList()))
        val result = testImpl.invoke(null, mockRecord)
        assertThat(result.updatedState).isNull()
    }

    @Test
    fun `first command metadata is added as expected`() {
        val state = RegistrationState("123", mock(), mock(), emptyList())
        whenever(resultFactory.call()).doReturn(RegistrationHandlerResult(state, emptyList()))

        val result = testImpl.invoke(state, mockRecord)

        assertThat(result.updatedState).isNotNull
        assertThat(result.updatedState?.previouslyCompletedCommands).containsExactlyInAnyOrderElementsOf(
            listOf(
                CompletedCommandMetadata(1, MyCommand::class.java.simpleName)
            )
        )
    }

    @Test
    fun `additional command metadata is added as expected`() {
        val state = RegistrationState("123", mock(), mock(), listOf(CompletedCommandMetadata(1, "FakeCommand")))
        whenever(resultFactory.call()).doReturn(RegistrationHandlerResult(state, emptyList()))

        val result = testImpl.invoke(state, mockRecord)

        assertThat(result.updatedState).isNotNull
        assertThat(result.updatedState?.previouslyCompletedCommands).containsExactlyInAnyOrderElementsOf(
            listOf(
                CompletedCommandMetadata(1, "FakeCommand"),
                CompletedCommandMetadata(2, MyCommand::class.java.simpleName)
            )
        )
    }
}
