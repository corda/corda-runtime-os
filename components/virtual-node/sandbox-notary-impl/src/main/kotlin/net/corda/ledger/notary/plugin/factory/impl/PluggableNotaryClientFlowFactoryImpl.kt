package net.corda.ledger.notary.plugin.factory.impl

import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory

@Component(
    service = [ PluggableNotaryClientFlowFactory::class, UsedByFlow::class, UsedByVerification::class, UsedByPersistence::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class PluggableNotaryClientFlowFactoryImpl @Activate constructor(
    @Reference(service = NotaryLookup::class)
    private val notaryLookup: NotaryLookup,
    @Reference(service = NotaryVirtualNodeSelectorService::class)
    private val virtualNodeSelectorService: NotaryVirtualNodeSelectorService
) : PluggableNotaryClientFlowFactory, SingletonSerializeAsToken, UsedByFlow, UsedByPersistence,
    UsedByVerification, CustomMetadataConsumer {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val pluggableNotaryClientFlowProviders = mutableMapOf<String, PluggableNotaryClientFlowProvider>()

    @Suspendable
    override fun create(notaryService: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        // TODO CORE-8856 If we use a lambda here it will throw an exception for some reason.
        //  For now we use an old fashioned for loop but will need further investigation.
        val pluginClass = notaryLookup.notaryServices.filter { it.name == notaryService.name }.firstOrNull()?.pluginClass

        if (pluginClass == null) {
            throw CordaRuntimeException(
                "Plugin class not found for notary service ${notaryService.name}. This means that no notary service " +
                        "matching this name has been registered on the network."
            )
        }

        val provider = pluggableNotaryClientFlowProviders[pluginClass]
            ?: throw CordaRuntimeException(
                "Notary flow provider not found for type: $pluginClass. This means no plugin has been installed for " +
                        "the given type. Please make sure you have a plugin provider class and it is annotated with " +
                        "the proper @PluggableNotaryType annotation."
            )

        return try {
            val selected = virtualNodeSelectorService.selectVirtualNode(notaryService)
            provider.create(selected, stx)
        } catch (e: Exception) {
            throw CordaRuntimeException("Exception while trying to create notary client with name: $pluginClass", e)
        }
    }

    @Suspendable
    override fun accept(context: MutableSandboxGroupContext) {
        installProviders(
            context.getMetadataServices()
        )
    }

    @Suspendable
    private fun installProviders(providers: Set<PluggableNotaryClientFlowProvider>) {
        providers.forEach { provider ->

            logger.debug("Installing plugin provider class: ${provider.javaClass.name}")

            val notaryProviderTypes = provider.javaClass.getAnnotationsByType(PluggableNotaryType::class.java)

            if (notaryProviderTypes.isEmpty()) {
                logger.warn(
                    "A @PluggableNotaryType annotation must exist on every PluggableNotaryClientFlowProvider " +
                            "but was not present in ${provider.javaClass.name}. Skipping provider."
                )
            } else if (notaryProviderTypes.size > 1) {
                // This should not be possible but having an extra check just in case
                logger.warn(
                    "The provider ${provider.javaClass.name} is annotated with multiple @PluggableNotaryType " +
                            "annotations. Skipping provider."
                )
            } else {
                val providerType = notaryProviderTypes.single().type
                if (pluggableNotaryClientFlowProviders[providerType] != null) {
                    logger.warn(
                        "A provider is already registered for the type: $providerType, it is not possible to " +
                                "register multiple providers for a single type. Skipping provider."
                    )
                } else {
                    pluggableNotaryClientFlowProviders[providerType] = provider
                    logger.debug("Provider ${provider.javaClass.name} has been installed for plugin: $providerType")
                }
            }
        }
    }
}
