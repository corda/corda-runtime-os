package net.corda.ledger.utxo.flow.impl.persistence

import io.micrometer.core.instrument.Timer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindGroupParameters
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.PersistSignedGroupParametersIfDoNotExist
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedGroupParametersExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedGroupParametersParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistSignedGroupParametersIfDoNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistSignedGroupParametersIfDoNotExistParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [UtxoLedgerGroupParametersPersistenceService::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
@Suppress("Unused")
class UtxoLedgerGroupParametersPersistenceServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = GroupParametersCache::class)
    private val groupParametersCache: GroupParametersCache
) : UtxoLedgerGroupParametersPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun find(hash: SecureHash): SignedGroupParameters? {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindGroupParameters) }) @Suspendable {
            groupParametersCache.get(hash) ?: wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindSignedGroupParametersExternalEventFactory::class.java,
                    FindSignedGroupParametersParameters(hash.toString())
                )
            }.singleOrNull()?.also { signedGroupParameters ->
                groupParametersCache.put(signedGroupParameters)
            }
        }
    }

    @Suspendable
    override fun persistIfDoesNotExist(signedGroupParameters: SignedGroupParameters) {
        recordSuspendable({ ledgerPersistenceFlowTimer(PersistSignedGroupParametersIfDoNotExist) }) @Suspendable {
            groupParametersCache.get(signedGroupParameters.hash) ?: wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistSignedGroupParametersIfDoNotExistExternalEventFactory::class.java,
                    with(signedGroupParameters) {
                        PersistSignedGroupParametersIfDoNotExistParameters(
                            groupParameters,
                            mgmSignature,
                            mgmSignatureSpec
                        )
                    }
                )
                groupParametersCache.put(signedGroupParameters)
            }
        }
    }

    private fun ledgerPersistenceFlowTimer(operationName: LedgerPersistenceMetricOperationName): Timer {
        return CordaMetrics.Metric.Ledger.PersistenceFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, operationName.name)
            .build()
    }
}
