package net.corda.interop.web3j.internal

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction

fun main() {
    EVMCommandProcessor().onNext(emptyList())
}

class EVMCommandProcessor(
): DurableProcessor<String, String> {

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        val web3j = Web3j.build(DelegatingWeb3JService())

        val account = "0x25a61c088a73AE3b47E266A326c92EFEf88A9A16"
//        val credentials = Credentials.create(account)

        val contractAddress = "0xF4b719B841048b21e9Ed60432E908B8e1ad81aaB"

        val getOwner = Function("getOwner", emptyList(), listOf(TypeReference.create(Address::class.java)))
        val encoded = FunctionEncoder.encode(getOwner)

        val response = web3j.ethCall(
            Transaction.createEthCallTransaction(account, contractAddress, encoded),
            DefaultBlockParameterName.LATEST
        ).send()
        println(response.value)

        val decoded = FunctionReturnDecoder.decode(response.value, getOwner.outputParameters)

        println(decoded)

//        val txManager = RawTransactionManager(web3j, credentials)

//        val hash = txManager.sendTransaction(
//            BigInteger.ONE,
//            BigInteger.ONE,
//            contractAddress,
//            encoded,
//            BigInteger.ONE,
//        ).transactionHash

//        val receiver = PollingTransactionReceiptProcessor(web3j, 1000, 1000)
//        val receipt = receiver.waitForTransactionReceipt(hash)

//        println(receipt)


        return emptyList()
    }
}