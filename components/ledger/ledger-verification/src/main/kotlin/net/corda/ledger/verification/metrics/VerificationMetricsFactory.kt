package net.corda.ledger.verification.metrics

import io.micrometer.core.instrument.Timer
import net.corda.ledger.utxo.transaction.verifier.ContractVerificationMetricFactory
import net.corda.metrics.CordaMetrics
import net.corda.virtualnode.HoldingIdentity

class VerificationMetricsFactory(val holdingIdentity: HoldingIdentity) : ContractVerificationMetricFactory {
    override fun getContractVerificationTimeMetric() =
        CordaMetrics.Metric.Ledger.ContractVerificationTime
            .builder()
            .forVirtualNode(holdingIdentity.shortHash.toString())
            .build()

    override fun getContractVerificationContractCountMetric() =
        CordaMetrics.Metric.Ledger.ContractVerificationContractCount
            .builder()
            .forVirtualNode(holdingIdentity.shortHash.toString())
            .build()

    override fun getContractVerificationContractTime(className: String): Timer {
        return CordaMetrics.Metric.Ledger.ContractVerificationContractTime
            .builder()
            .forVirtualNode(holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.LedgerContractName, className)
            .build()
    }
}
