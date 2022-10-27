package net.corda.testing.singletons.two

import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.v5.testing.MessageProvider
import net.corda.v5.testing.uuid.UUIDProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.osgi.service.component.propertytypes.ServiceRanking

@Component(
    service = [ MessageProvider::class, SingletonSerializeAsToken::class ],
    name = "message.default",
    scope = PROTOTYPE
)
@ServiceRanking(0)
class MessageDefault @Activate constructor(
    @Reference
    uuidProvider: UUIDProvider
) : MessageProvider, SingletonSerializeAsToken {
    private val uuid = uuidProvider.uuid

    override fun getMessage(): String {
        return uuid.toString()
    }

    override fun toString(): String {
        return "MessageDefault[$message]"
    }
}
