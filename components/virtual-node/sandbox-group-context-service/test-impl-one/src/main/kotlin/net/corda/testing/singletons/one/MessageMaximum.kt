package net.corda.testing.singletons.one

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.testing.MessageProvider
import net.corda.v5.testing.uuid.UUIDProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.osgi.service.component.propertytypes.ServiceRanking

@Component(
    service = [ MessageProvider::class, UsedByFlow::class ],
    property = [ "test.ranked=maximum" ],
    name = "message.maximum",
    scope = PROTOTYPE
)
@ServiceRanking(Int.MAX_VALUE)
class MessageMaximum @Activate constructor(
    @Reference
    uuidProvider: UUIDProvider
) : MessageProvider, UsedByFlow {
    private val uuid = uuidProvider.uuid

    override fun getMessage(): String {
        return uuid.toString()
    }

    override fun toString(): String {
        return "MessageMaximum[$message]"
    }
}
