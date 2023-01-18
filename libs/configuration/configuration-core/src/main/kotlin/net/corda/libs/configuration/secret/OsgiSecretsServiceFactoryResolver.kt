package net.corda.libs.configuration.secret

import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

@Component(
    service = [SecretsServiceFactoryResolver::class],
    reference = [
        Reference(
            name = OsgiSecretsServiceFactoryResolver.SECRETS_SERVICE_FACTORY_SERVICE_NAME,
            service = SecretsServiceFactory::class,
            cardinality = ReferenceCardinality.MULTIPLE
        )
    ]
)
class OsgiSecretsServiceFactoryResolver @Activate constructor(
    private val componentContext: ComponentContext
)    : SecretsServiceFactoryResolver {

    companion object {
        const val SECRETS_SERVICE_FACTORY_SERVICE_NAME = "SecretsServiceFactories"
    }

    override fun findAll(): Collection<SecretsServiceFactory> {
        return componentContext.locateServices(SECRETS_SERVICE_FACTORY_SERVICE_NAME)
            ?.filterIsInstance<SecretsServiceFactory>() ?: emptyList()
    }
}
