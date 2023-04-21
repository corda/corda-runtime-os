package net.corda.testing.singletons.two

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.testing.MapProvider
import net.corda.v5.testing.MessageProvider
import net.corda.v5.testing.NoSuchService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.AT_LEAST_ONE
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("unused", "LongParameterList")
@Component(
    name = "map.multiples",
    service = [ MapProvider::class, UsedByFlow::class ],
    scope = PROTOTYPE
)
class MapProviderImpl @Activate constructor(
    @Reference(cardinality = AT_LEAST_ONE)
    private val allMessages: List<MessageProvider>,
    @Reference(cardinality = AT_LEAST_ONE, target = "(test.ranked=*)")
    private val targetedMessages: List<MessageProvider>,
    @Reference(target = "(component.name=does.not.exist)")
    private val unmatchedServices: List<MessageProvider>?,
    @Reference
    private val optionalServices: List<NoSuchService>?
): MapProvider, UsedByFlow {
    override fun getMap(): Map<String, *> {
        return mapOf(
            "all" to allMessages,
            "targeted" to targetedMessages,
            "unmatchedServices" to (unmatchedServices ?: "NO MATCHES"),
            "optionalServices" to (optionalServices ?: "MISSING")
        )
    }
}
