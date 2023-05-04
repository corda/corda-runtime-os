package net.corda.flow.application.services.impl

import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeReader
import java.io.Reader

@Component(service = [FacadeReader::class, UsedByFlow::class], scope = PROTOTYPE)
class InteropFacadeImpl @Activate constructor(
    @Reference(service = SerializationServiceInternal::class)
    private val serializationService: SerializationServiceInternal
) : FacadeReader, UsedByFlow, SingletonSerializeAsToken {

    override fun read(reader: Reader): Facade {
        TODO()
    }

    @Suspendable
    override fun read(input: String): Facade {
        return FacadeReaders.JSON.read(input)
    }
}
