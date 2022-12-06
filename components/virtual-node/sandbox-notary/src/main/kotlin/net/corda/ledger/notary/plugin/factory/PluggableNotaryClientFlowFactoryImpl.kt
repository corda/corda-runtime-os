package net.corda.ledger.notary.plugin.factory

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.notary.plugin.core.PluggableNotaryClientFlowFactory
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ PluggableNotaryClientFlowFactory::class, UsedByFlow::class ],
    scope = ServiceScope.PROTOTYPE
)
class PluggableNotaryClientFlowFactoryImpl @Activate constructor()
    : SingletonSerializeAsToken, PluggableNotaryClientFlowFactory, UsedByFlow, CustomMetadataConsumer {

    private companion object {
        val logger = loggerFor<PluggableNotaryClientFlowFactoryImpl>()
    }

    private val pluggableNotaryClientFlowProviders = mutableMapOf<String, PluggableNotaryClientFlowProvider>()

    @Suspendable
    override fun create(notary: Party, type: String, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        val provider = pluggableNotaryClientFlowProviders[type]
            ?: throw IllegalStateException("Notary flow provider not found for type: $type")

        return try {
            provider.create(notary, stx)
        } catch (e: Exception) {
            throw CordaRuntimeException("Exception while trying to create notary client with name: $type, " +
                    "caused by: ${e.message}")
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

            logger.info("Installing plugin provider: ${provider.javaClass.name}.")

            val notaryProviderTypes = provider.javaClass.getAnnotationsByType(PluggableNotaryType::class.java)

            if (notaryProviderTypes.isEmpty()) {
                logger.warn(
                    "A @PluggableNotaryType annotation must exist on every PluggableNotaryClientFlowProvider " +
                            "but was not present in ${provider.javaClass.name}."
                )
            } else {
                logger.info("Plugin provider: ${provider.javaClass.name} has been installed.")
                pluggableNotaryClientFlowProviders[notaryProviderTypes.first().type] = provider
            }
        }
    }
}
