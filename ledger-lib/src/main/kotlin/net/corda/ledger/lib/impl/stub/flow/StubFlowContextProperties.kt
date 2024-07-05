package net.corda.ledger.lib.impl.stub.flow

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowContextPropertyKeys

class StubFlowContextProperties : FlowContextProperties {

    // TODO Come up with fake CPI
    override fun get(key: String): String? {
        return when (key) {
            FlowContextPropertyKeys.CPI_NAME -> ""
            FlowContextPropertyKeys.CPI_VERSION -> ""
            FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH -> ""
            FlowContextPropertyKeys.CPI_FILE_CHECKSUM -> ""
            else -> throw IllegalArgumentException("¯\\_(ツ)_/¯")
        }
    }

    override fun put(key: String, value: String) {
        TODO("Not yet implemented")
    }
}