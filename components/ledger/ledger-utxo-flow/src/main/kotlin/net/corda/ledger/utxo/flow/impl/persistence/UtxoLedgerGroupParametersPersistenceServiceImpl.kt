package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.flow.transaction.SignedGroupParametersContainer
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedGroupParametersExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedGroupParametersParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistSignedGroupParametersIfDoNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistSignedGroupParametersIfDoNotExistParameters
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ UtxoLedgerGroupParametersPersistenceService::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
@Suppress("Unused")
class UtxoLedgerGroupParametersPersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoLedgerGroupParametersPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun find(hash: SecureHash): SignedGroupParametersContainer? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindSignedGroupParametersExternalEventFactory::class.java,
                FindSignedGroupParametersParameters(hash.toString())
            )
        }.firstOrNull().let {
            if (it == null) {
                null
            } else {
                serializationService.deserialize<SignedGroupParametersContainer>(it.array()) // todo another format?
            }
        }
    }

    @Suspendable
    override fun persistIfDoesNotExist(signedGroupParameters: SignedGroupParametersContainer) {
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistSignedGroupParametersIfDoNotExistExternalEventFactory::class.java,
                with(signedGroupParameters) {
                    PersistSignedGroupParametersIfDoNotExistParameters(
                        bytes,
                        signature,
                        signatureSpec
                    )
                }
            )
        }
    }

}
