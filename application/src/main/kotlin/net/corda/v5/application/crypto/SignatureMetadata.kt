package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.CordaSerializable

/**
 * SignatureMeta is required to add extra meta-data to a transaction's signature.
 * It currently supports platformVersion only, but it can be extended to support a universal digital
 * signature model enabling partial signatures and attaching extra information, such as a user's timestamp or other
 * application-specific fields.
 *
 * @param platformVersion current DLT version.
 */
@CordaSerializable
data class SignatureMetadata(val platformVersion: Int)
