package net.corda.testing.singletons.uuid

import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.v5.testing.uuid.UUIDProvider
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.util.UUID

@Component(
    service = [ UUIDProvider::class, SingletonSerializeAsToken::class ],
    scope = PROTOTYPE
)
class UUIDProviderImpl : UUIDProvider, SingletonSerializeAsToken {
    private val uuid = UUID.randomUUID()
    override fun getUUID(): UUID = uuid
}
