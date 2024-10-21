package net.corda.ledger.common.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.libs.common.flow.impl.transaction.factory.TransactionMetadataFactoryImpl
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [TransactionMetadataFactory::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class TransactionMetadataFactoryOsgiImpl(
    delegate: TransactionMetadataFactory
) : TransactionMetadataFactory by delegate, UsedByFlow, SingletonSerializeAsToken {

    @Activate
    constructor(
        @Reference(service = CurrentSandboxGroupContext::class)
        currentSandboxGroupContext: CurrentSandboxGroupContext,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
        @Reference(service = FlowEngine::class)
        flowEngine: FlowEngine
    ) : this(
        TransactionMetadataFactoryImpl(
            {
                currentSandboxGroupContext
                    .get()
                    .sandboxGroup
                    .metadata
                    .values
                    .filter { it.isContractCpk() }
                    .map { cpk ->
                        CordaPackageSummaryImpl(
                            name = cpk.cpkId.name,
                            version = cpk.cpkId.version,
                            signerSummaryHash = cpk.cpkId.signerSummaryHash.toString(),
                            fileChecksum = cpk.fileChecksum.toString()
                        )
                    }
            },
            platformInfoProvider,
            flowEngine
        )
    )
}
