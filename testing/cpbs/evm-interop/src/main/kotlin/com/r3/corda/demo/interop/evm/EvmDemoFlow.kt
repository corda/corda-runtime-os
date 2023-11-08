package com.r3.corda.demo.interop.evm

import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.TransactionOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

/**
 * The Evm Demo Flow is solely for demoing access to the EVM from Corda.
 */
@Suppress("unused")
class EvmDemoFlow : ClientStartableFlow {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val TRANSFER_FUNCTION = "sendTokenOne"
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var evmService: EvmService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Evm Demo Flow...")
        try {
            // Get any of the relevant details from the request here
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, EvmDemoInput::class.java)



            val dummyGasNumber = BigInteger("a41c5", 16)
            val transactionOptions = TransactionOptions(
                dummyGasNumber,                 // gasLimit
                0.toBigInteger(),               // value
                20000000000.toBigInteger(),     // maxFeePerGas
                20000000000.toBigInteger(),     // maxPriorityFeePerGas
                inputs.rpcUrl!!,                // rpcUrl
                inputs.buyerAddress,          // from
            )

            val parameters = listOf(
                Parameter.of("from", Type.ADDRESS, inputs.buyerAddress!!),
                Parameter.of("to", Type.ADDRESS, inputs.sellerAddress!!),
                Parameter.of("id", Type.UINT256, 1.toBigInteger()),
                Parameter.of("amount", Type.UINT256, inputs.fractionPurchased!!.toBigInteger()),
                Parameter.of("data", Type.BYTES, ""),
            )

            val hash = this.evmService.transaction(
                "safeTransferFrom",
                inputs.contractAddress,
                transactionOptions,
                parameters
            )
            // Step 2.  Call to the Evm to do the asset transfer


            val response = EvmDemoOutput(hash)
            return jsonMarshallingService.format(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}