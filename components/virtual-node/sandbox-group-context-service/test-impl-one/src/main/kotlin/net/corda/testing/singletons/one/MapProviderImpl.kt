package net.corda.testing.singletons.one

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.testing.MapProvider
import net.corda.v5.testing.MessageProvider
import net.corda.v5.testing.NoSuchService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("unused", "LongParameterList")
@Component(
    name = "map.singles",
    service = [ MapProvider::class, UsedByFlow::class ],
    scope = PROTOTYPE
)
class MapProviderImpl @Activate constructor(
    @Reference(target = "(component.name=message.maximum)")
    private val messageMaximum: MessageProvider,
    @Reference(target = "(component.name=message.minimum)")
    private val messageMinimum: MessageProvider,
    @Reference(target = "(component.name=message.default)")
    private val messageDefault: MessageProvider,
    @Reference
    private val message: MessageProvider,
    @Reference(cardinality = OPTIONAL, target = "(component.name=does.not.exist)")
    private val unmatchedService: MessageProvider?,
    @Reference(cardinality = OPTIONAL)
    private val optionalService: NoSuchService?
): MapProvider, UsedByFlow {
    override fun getMap(): Map<String, *> {
        return mapOf(
            "unmatchedService" to (unmatchedService ?: "NO MATCH"),
            "optionalService" to (optionalService ?: "MISSING"),
            "targetMaximum" to messageMaximum,
            "targetMinimum" to messageMinimum,
            "targetDefault" to messageDefault,
            "default" to message
        )
    }
}
