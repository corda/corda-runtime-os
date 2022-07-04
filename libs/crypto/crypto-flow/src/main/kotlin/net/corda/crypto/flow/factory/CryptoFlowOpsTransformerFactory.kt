package net.corda.crypto.flow.factory

import net.corda.crypto.flow.CryptoFlowOpsTransformer

interface CryptoFlowOpsTransformerFactory {

    fun create(requestingComponent: String, responseTopic: String, requestValidityWindowSeconds: Long = 300): CryptoFlowOpsTransformer
}