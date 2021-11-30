package net.corda.flow.manager.impl

import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.services.json.JsonMarshallingService

class JsonMarshallingServiceProxy(
    private val flowStateMachine: FlowStateMachine<*>,
    private val SandboxGroup: SandboxGroup,
    private val singleInstanceService: JsonMarshallingService
) : JsonMarshallingService {

    override fun formatJson(input: Any): String {
        return singleInstanceService.formatJson(input)
    }

    override fun <T> parseJson(input: String, clazz: Class<T>): T {
        return singleInstanceService.parseJson(input,clazz)
    }

    override fun <T> parseJsonList(input: String, clazz: Class<T>): List<T> {
        return singleInstanceService.parseJsonList(input,clazz)
    }

}