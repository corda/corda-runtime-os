package net.corda.v5.ledger.transactions

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.exceptions.CordaThrowable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey
import java.security.SignatureException

class SignaturesMissingException(val missing: Set<PublicKey>, val descriptions: List<String>, val id: SecureHash) :
    SignatureException(missingSignatureMsg(missing, descriptions, id)),
    CordaThrowable by CordaRuntimeException(missingSignatureMsg(missing, descriptions, id)) {
    private companion object {
        private fun missingSignatureMsg(missing: Set<PublicKey>, descriptions: List<String>, id: SecureHash): String {
            return "Missing signatures on transaction ${id.prefixChars()} for " +
                    "keys: ${missing.joinToString { it.toStringShort() }}, " +
                    "by signers: ${descriptions.joinToString()} "
        }
    }
}
