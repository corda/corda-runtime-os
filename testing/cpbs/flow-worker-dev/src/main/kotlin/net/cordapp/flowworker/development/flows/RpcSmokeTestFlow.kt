package net.cordapp.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.messages.InitiatedSmokeTestMessage
import net.cordapp.flowworker.development.messages.RpcSmokeTestInput
import net.cordapp.flowworker.development.messages.RpcSmokeTestOutput

@Suppress("unused")
@InitiatingFlow(protocol = "smoke-test-protocol")
class RpcSmokeTestFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    private val commandMap: Map<String, (RpcSmokeTestInput) -> RpcSmokeTestOutput> = mapOf(
        "echo" to this::echo,
        "throw_error" to this::throwError,
        "start_sessions" to this::startSessions,
    )

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val request = requestBody.getRequestBodyAs<RpcSmokeTestInput>(jsonMarshallingService)
        return jsonMarshallingService.format(request.execute())
    }

    private fun echo(input: RpcSmokeTestInput): RpcSmokeTestOutput {
        return RpcSmokeTestOutput().apply {
            command = "echo"
            result = input.getValue("echo_value")
        }
    }

    private fun throwError(input: RpcSmokeTestInput): RpcSmokeTestOutput {
        throw IllegalStateException(input.getValue("error_message"))
    }

    @Suspendable
    private fun startSessions(input: RpcSmokeTestInput): RpcSmokeTestOutput {
        val sessions = input.getValue("sessions").split(";")
        val messages = input.getValue("messages").split(";")
        if (sessions.size != messages.size) {
            throw IllegalStateException("Sessions test run with unmatched messages to sessions")
        }

        log.info("Starting sessions for '${input.getValue("sessions")}'")
        val outputs = mutableListOf<String>()
        sessions.forEachIndexed { idx, x500 ->
            log.info("Creating session for '${x500}'...")
            val session = flowMessaging.initiateFlow(MemberX500Name.parse(x500))

            log.info("Creating session '${session}' now sending and waiting for response ...")
            val response = session
                .sendAndReceive<InitiatedSmokeTestMessage>(InitiatedSmokeTestMessage(messages[idx]))
                .unwrap { it }

            log.info("Received response from session '${session}'.")

            outputs.add("${x500}=${response.message}")
        }

        return RpcSmokeTestOutput().apply {
            command = "start_sessions"
            result = outputs.joinToString("; ")
        }
    }

    private fun RpcSmokeTestInput.getValue(key: String): String {
        return checkNotNull(this.data?.get(key)) { "Failed to find key '${key}' in the RPC input args" }
    }

    private fun RpcSmokeTestInput.execute(): RpcSmokeTestOutput {
        val inputCommand = this.command
        return checkNotNull(commandMap[this.command]) { "command '${this.command}' not recognised" }
            .invoke(this).apply { command = inputCommand }
    }
}