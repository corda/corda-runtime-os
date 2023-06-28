package net.corda.interop.rest.impl.v1

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.identity.cache.InteropIdentityCacheService
import net.corda.interop.identity.write.InteropIdentityWriteService
import net.corda.libs.interop.endpoints.v1.InteropRestResource
import net.corda.libs.interop.endpoints.v1.types.RestInteropIdentity
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.rest.PluggableRestResource
import net.corda.rest.response.ResponseEntity
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

@Component(service = [PluggableRestResource::class])
internal class InteropRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = InteropIdentityCacheService::class)
    private val interopIdentityCacheService: InteropIdentityCacheService,
    @Reference(service = InteropIdentityWriteService::class)
    private val interopIdentityWriteService: InteropIdentityWriteService
) : InteropRestResource, PluggableRestResource<InteropRestResource>, Lifecycle {

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    // RestResource values
    override val targetInterface: Class<InteropRestResource> = InteropRestResource::class.java
    override fun getInterOpGroups(holdingidentityshorthash: String): Map<UUID, String> {
        return mapOf(
            Pair(
                UUID.randomUUID(),
                """
                    {
                       "fileFormatVersion":1,
                       "groupId":"3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08",
                       "registrationProtocol":".....
                     … //all content from our hardcoded group policy file"
                    }
                """.trimIndent()
            )
        )
    }

    override fun createInterOpIdentity(
        restInteropIdentity: RestInteropIdentity,
        holdingidentityshorthash: String
    ): ResponseEntity<String> {
        interopIdentityWriteService.addInteropIdentity(
            holdingidentityshorthash,
            restInteropIdentity.groupId.toString(),
            restInteropIdentity.x500Name
        )

        logger.info("AliasIdentity created.")

        return ResponseEntity.ok("OK")
    }

    override fun getInterOpIdentities(holdingidentityshorthash: String): List<RestInteropIdentity> {
        val groupToAliasMappings = interopIdentityCacheService.getAliasIdentities(holdingidentityshorthash)
        return groupToAliasMappings.map {
            RestInteropIdentity(
                it.value.aliasX500Name,
                UUID.fromString(it.value.groupId)
            )
        }.toList()
    }

    override val protocolVersion = 1

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::interopIdentityCacheService,
        ::interopIdentityWriteService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<InteropRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }

                    LifecycleStatus.UP -> {
                        // Receive updates to the REST and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }

                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
            }
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()

}