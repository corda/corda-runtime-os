package net.corda.ledger.utxo.flow.impl.groupparameters.verifier

import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/*
 * Verify Signed Group parameters consistency. (hash and MGM signature.)
 */
@Component(
    service = [ SignedGroupParametersVerifier::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
@Suppress("Unused")
class SignedGroupParametersVerifierImpl @Activate constructor(
    @Reference(service = SignatureVerificationService::class)
    private val signatureVerificationService: SignatureVerificationService,
    @Reference(service = GroupParametersLookupInternal::class)
    private val groupParametersLookup: GroupParametersLookupInternal
) : SignedGroupParametersVerifier, UsedByFlow, SingletonSerializeAsToken {

    override fun verify(
        transaction: UtxoLedgerTransaction,
        signedGroupParameters: SignedGroupParameters?
    ) {
        requireNotNull(signedGroupParameters) {
            "Signed group parameters referenced in the transaction metadata not found. [" +
                (transaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash() + "]"
        }
        verifyHash(transaction, signedGroupParameters)
        verifySignature(signedGroupParameters)
    }

    private fun verifyHash(transaction: UtxoLedgerTransaction, signedGroupParameters: SignedGroupParameters) {
        check(
            (transaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash() ==
                signedGroupParameters.hash.toString()
        ) {
            "The referenced hash in the metadata did not match with the one returned from the database."
        }
    }

    override fun verifySignature(signedGroupParameters: SignedGroupParameters) {
        check(groupParametersLookup.getMgmKeys().contains(signedGroupParameters.mgmSignature.by)) {
            "The group parameters is not signed with a recognized MGM public key."
        }
        signatureVerificationService.verify(
            signedGroupParameters.groupParameters,
            signedGroupParameters.mgmSignature.bytes,
            signedGroupParameters.mgmSignature.by,
            signedGroupParameters.mgmSignatureSpec,
        )
    }
}
