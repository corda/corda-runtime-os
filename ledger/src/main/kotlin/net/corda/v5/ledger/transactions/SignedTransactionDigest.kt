package net.corda.v5.ledger.transactions

import net.corda.v5.application.utilities.JsonRepresentable
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.util.toBase64
import net.corda.v5.application.crypto.DigitalSignatureAndMeta
import net.corda.v5.crypto.SecureHash

/**
 * Extract of data from [SignedTransaction], primarily to be sensibly represented in JSON
 * Also useful on the client side to inspect what was actually returned as result of the flow
 *
 * @property txId - The hex string of the transaction's ID
 * @property outputStates - The transaction's output states formatted as JSON
 * @property signatures - The Base64-encoded bytes of the transaction's signatures
 */
@CordaSerializable
data class SignedTransactionDigest(val txId: String, val outputStates: List<String>, val signatures: List<String>) : JsonRepresentable {

    /** A utility constructor that avoids the need to format the transaction's ID and signatures. */
    constructor(txIdUnformatted: SecureHash, outputStates: List<String>, signaturesUnformatted: List<DigitalSignatureAndMeta>) :
            this(txIdUnformatted.toString(), outputStates, signaturesUnformatted.map { it.bytes.toBase64() })

    private fun String.embedJson() = this.replace("\"", "\\\"")

    override fun toJsonString(): String {
        val outputStatesStr = outputStates.joinToString(prefix = "[", postfix = "]") { "\"${it.embedJson()}\"" }
        val signaturesStr = signatures.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return """
               |{ 
               | "txId" : "$txId",
               | "outputStates" : $outputStatesStr, 
               | "signatures": $signaturesStr
               |}""".trimMargin()
    }
}