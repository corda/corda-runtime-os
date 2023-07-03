package net.corda.interop.web3j

import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignService

class DelegatingTxSignService : TxSignService {
    override fun sign(rawTransaction: RawTransaction, chainId: Long): ByteArray {
        TODO("Not yet implemented")
    }

    override fun getAddress(): String {
        // return a public key from Ganache at the moment
        return "0x7B50CC6a4893985d373B87FED5B8163bB022a165"
    }
}