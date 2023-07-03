package com.r3.corda.testing.calculator

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider

class CalculatorFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var web3j: Web3j

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Calculator starting...")
        var resultMessage = ""
        try {
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)
            val result = (inputs.a ?: 0) + (inputs.b ?: 0)
            log.info("Calculated result ${inputs.a} + ${inputs.b} = ${result}, formatting for response...")
            val outputFormatter = OutputFormattingFlow(result)
            resultMessage = flowEngine.subFlow(outputFormatter)
            log.info("Calculated response:  $resultMessage")
        } catch (e: Exception) {
            log.warn(":( could not complete calculation of '$requestBody' because:'${e.message}'")
        }
        log.info("Calculation completed.")

        val erc20 = ERC20.load("somestring", web3j, RawTransactionManager(web3j, Credentials.create("")), DefaultGasProvider())
        println(erc20)
        return resultMessage
    }
}
