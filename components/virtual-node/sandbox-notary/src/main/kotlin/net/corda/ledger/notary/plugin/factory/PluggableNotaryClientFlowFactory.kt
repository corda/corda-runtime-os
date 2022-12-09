package net.corda.ledger.notary.plugin.factory

import net.corda.ledger.notary.worker.selection.NotaryWorkerSelectorService
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.loggerFor
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
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ UsedByFlow::class ],
    property = [ "corda.marker.only:Boolean=true" ],
    scope = ServiceScope.PROTOTYPE
)
class PluggableNotaryClientFlowFactory @Activate constructor(
    @Reference(service = NotaryLookup::class)
    private val notaryLookup: NotaryLookup,
    @Reference(service = NotaryWorkerSelectorService::class)
    private val workerSelectorService: NotaryWorkerSelectorService,
    @Reference(service = NotaryVirtualNodeLookup::class)
    private val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
) : SingletonSerializeAsToken, UsedByFlow, CustomMetadataConsumer {

    private companion object {
        val logger = loggerFor<PluggableNotaryClientFlowFactory>()
    }

    private val pluggableNotaryClientFlowProviders = mutableMapOf<String, PluggableNotaryClientFlowProvider>()

    @Suspendable
    fun create(notaryService: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        // TODO CORE-8856 If we use a lambda here it will throw an exception for some reason.
        //  For now we use an old fashioned for loop but will need further investigation.
        var pluginClass: String? = null
        for (notaryInfo in notaryLookup.notaryServices) {
            if (notaryInfo.name == notaryService.name && notaryInfo.publicKey == notaryService.owningKey) {
                pluginClass = notaryInfo.pluginClass
            }
        }

        val nextWorker = selectWorker(notaryService)
        val provider = pluggableNotaryClientFlowProviders[pluginClass]
            ?: throw IllegalStateException("Notary flow provider not found for type: $pluginClass")

        return try {
            provider.create(nextWorker, stx)
        } catch (e: Exception) {
            throw CordaRuntimeException("Exception while trying to create notary client with name: $pluginClass", e)
        }
    }

    @Suspendable
    private fun selectWorker(notaryService: Party): Party {
        val workers = notaryVirtualNodeLookup.getNotaryVirtualNodes(notaryService.name)
        return workerSelectorService.next(
            workers.map { Party(it.name, it.sessionInitiationKey) }
        )
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

            logger.info("Installing plugin provider: ${provider.javaClass.name}.")

            val notaryProviderTypes = provider.javaClass.getAnnotationsByType(PluggableNotaryType::class.java)

            if (notaryProviderTypes.isEmpty()) {
                logger.warn(
                    "A @PluggableNotaryType annotation must exist on every PluggableNotaryClientFlowProvider " +
                            "but was not present in ${provider.javaClass.name}. Skipping provider."
                )
                return@forEach
            }

            if (notaryProviderTypes.size > 1) {
                // This should not be possible but having an extra check just in case
                logger.warn(
                    "The provider ${provider.javaClass.name} is annotated with multiple @PluggableNotaryType " +
                            "annotations. Skipping provider."
                )
                return@forEach
            }

            val providerType = notaryProviderTypes.single().type
            val currentProvider = pluggableNotaryClientFlowProviders[providerType]
            if (currentProvider != null) {
                logger.warn(
                    "A provider is already registered for the type: $providerType, it is not possible to " +
                            "register multiple providers for a single type. Skipping provider."
                )
            } else {
                pluggableNotaryClientFlowProviders[providerType] = provider
            }
        }
    }
}
