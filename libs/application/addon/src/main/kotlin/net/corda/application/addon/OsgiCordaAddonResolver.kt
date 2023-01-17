package net.corda.application.addon

import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

@Component(
    service = [CordaAddonResolver::class],
    reference = [
        Reference(
            name = OsgiCordaAddonResolver.ADDONS_SERVICE_NAME,
            service = CordaAddonResolver::class,
            cardinality = ReferenceCardinality.MULTIPLE
        )
    ]
)
class OsgiCordaAddonResolver @Activate constructor(
    private val componentContext: ComponentContext
)    : CordaAddonResolver {

    companion object {
        const val ADDONS_SERVICE_NAME = "CordaAddon"
    }

    override fun findAll(): Collection<CordaAddon> {
        return componentContext.locateServices(ADDONS_SERVICE_NAME)
            ?.filterIsInstance<CordaAddon>() ?: emptyList()
    }
}
