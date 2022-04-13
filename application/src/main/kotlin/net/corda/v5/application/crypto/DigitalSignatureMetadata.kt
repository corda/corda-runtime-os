package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

/**
 * Metadata attached to a signature.
 *
 * The use of this metadata is decided by API layers above application. For example, the ledger implementation may
 * populate some properties when transaction signatures are requested.
 *
 * Note that the metadata itself is not signed over.
 *
 * @property timestamp The timestamp at which the signature was applied
 * @property properties A set of properties for this signature. Content depends on API layers above application.
 */
@CordaSerializable
data class DigitalSignatureMetadata(val timestamp: Instant,
                                    val properties: Map<String, String>)
