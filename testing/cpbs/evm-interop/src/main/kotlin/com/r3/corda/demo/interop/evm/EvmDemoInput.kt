package com.r3.corda.demo.interop.evm


class EvmDemoInput {
    var rpcUrl: String? = null
    var buyerAddress: String? = null
    var sellerAddress: String? = null
    var fractionPurchased: Int? = null
    var purchasePrice: Int? = null
    var contractAddress: String? = null
}

class EvmDemoTxnReceiptInput {
    var rpcUrl: String? = null
    var hash: String? = null
}

class EvmDemoCallInput {
    var rpcUrl: String? = null
    var contractAddress: String? = null
    var address: String? = null
}