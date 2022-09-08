package net.corda.flow.application.serialization

import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [SerializationService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class SerializationServiceImpl @Activate constructor(
    @Reference(service = FlowFiberSerializationService::class)
    private val flowFiberSerializationService: FlowFiberSerializationService
) : SerializationService by flowFiberSerializationService, SingletonSerializeAsToken