package net.corda.flow.manager.impl

import net.corda.dependency.injection.InjectableFactory
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.sandbox.SandboxGroup
import net.corda.services.JsonMarshallingServiceInternal
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InjectableFactory::class])
class JsonMarshallingServiceFactory @Activate constructor(
    @Reference(service = JsonMarshallingServiceInternal::class)
    private val instance: JsonMarshallingServiceInternal
) : InjectableFactory<JsonMarshallingService> {

    override val target: Class<JsonMarshallingService> = JsonMarshallingService::class.java

    override fun create(
        flowStateMachine: FlowStateMachine<*>,
        sandboxGroup: SandboxGroup
    ): JsonMarshallingService {
        return JsonMarshallingServiceProxy(flowStateMachine,sandboxGroup, instance)
    }

    override fun getSingletons(): Set<SingletonSerializeAsToken> {
        return setOf(instance)
    }
}

