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
import net.corda.v5.application.membership.MemberLookup
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
    lateinit var memberLookupService: MemberLookup

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

            // Step 1.  Do the token transfer on Corda



            // Step 2.  Call to the Evm to do the asset transfer
            val dummyGasNumber = BigInteger("a41c5", 16)
            val transactionOptions = TransactionOptions(
                dummyGasNumber,
                0.toBigInteger(),
                20000000000.toBigInteger(),
                20000000000.toBigInteger(),
                inputs.rpcUrl!!,
                inputs.buyerAddress!!,
            )

            val parameters = listOf(
                Parameter.of("from", Type.ADDRESS, inputs.buyerAddress!!),
                Parameter.of("to", Type.ADDRESS, inputs.sellerAddress!!),
                Parameter.of("amount", Type.UINT256, inputs.fractionPurchased!!.toBigInteger()),
            )

            val hash = evmService.transaction(
                TRANSFER_FUNCTION,
                inputs.contractAddress!!,
                transactionOptions,
                parameters
            )

            val response = EvmDemoOutput(hash)

            return jsonMarshallingService.format(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}

