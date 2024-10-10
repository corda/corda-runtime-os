package net.corda.ledger.common.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.CordaPackageSummary

fun FlowEngine.getCpiSummary(): CordaPackageSummaryImpl =
    CordaPackageSummaryImpl(
        name = flowContextProperties[FlowContextPropertyKeys.CPI_NAME]
            ?: throw CordaRuntimeException("CPI name is not accessible"),
        version = flowContextProperties[FlowContextPropertyKeys.CPI_VERSION]
            ?: throw CordaRuntimeException("CPI version is not accessible"),
        signerSummaryHash = flowContextProperties[FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH]
            ?: throw CordaRuntimeException("CPI signer summary hash is not accessible"),
        fileChecksum = flowContextProperties[FlowContextPropertyKeys.CPI_FILE_CHECKSUM]
            ?: throw CordaRuntimeException("CPI file checksum is not accessible"),
    )
