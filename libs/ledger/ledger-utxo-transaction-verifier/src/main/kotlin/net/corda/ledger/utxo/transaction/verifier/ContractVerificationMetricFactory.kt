package net.corda.ledger.utxo.transaction.verifier

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Timer

interface ContractVerificationMetricFactory {

    fun getContractVerificationTimeMetric(): Timer

    fun getContractVerificationContractCountMetric(): DistributionSummary

    fun getContractVerificationContractTime(className: String): Timer
}
