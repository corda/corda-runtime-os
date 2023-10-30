package net.corda.interop.evm.decoder


import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.AbiTypes

/**
 * TransactionDecoder Class
 *
 * This class facilitates decoding of call outputs from the EVM
 *
 */
class TransactionDecoder {

    companion object {
        /**
         * Starts the encoding process by encoding the function signature and its inputs
         *
         * @param params Takes the Parameter specified in the Corda API.
         * @return The encoded function data that can be sending a transaction
         */
        fun decode(value: String, type: String): String {
            val result = if (type === "string") {
                java.lang.String(value.toByteArray(), "UTF-8").toString()
            } else {
                TypeDecoder.decode(value, AbiTypes.getType(type)).value.toString()
            }
            return jacksonObjectMapper().writeValueAsString(result)
        }
    }
}