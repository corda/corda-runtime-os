package net.corda.testing.singletons.one

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
    property = [ "test.ranked=minimum" ],
    name = "message.minimum",
    scope = PROTOTYPE
)
@ServiceRanking(Int.MIN_VALUE)
class MessageMinimum @Activate constructor(
    @Reference
    uuidProvider: UUIDProvider
) : MessageProvider, SingletonSerializeAsToken {
    private val uuid = uuidProvider.uuid

    override fun getMessage(): String {
        return uuid.toString()
    }

    override fun toString(): String {
        return "MessageMinimum[$message]"
    }
}
