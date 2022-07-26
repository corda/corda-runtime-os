package net.corda.membership.httprpc.v1.types.response

import java.time.Duration
import java.time.Instant

/**
 * Information on a hardware security module (HSM)
 */
data class HsmInfo(
    /**
     * The configuration ID
     */
    val id: String,

    /**
     * Timestamp in which the record was updated or added.
     */
    val createdAt: Instant,

    /**
     * Label associated with HSM worker to partition for HSMs which don't support more than one HSM per process/VM.
     */
    val workerLabel: String?,

    /**
     * Human-readable description of the HSM instance
     */
    val description: String?,

    /**
     * Max number of attempts when calling the HSM.
     */
    val maxAttempts: Int,

    /**
     * For how long to wait for a response on each attempt.
     */
    val attemptTimeout: Duration,

    /**
     * How to generate wrapping key on the HSM registration
     */
    val masterKeyPolicy: String,

    /**
     * If masterKeyPolicy=SHARED then this field must be specified with the wrapping key name
     */
    val masterKeyAlias: String?,

    /**
     * List of supported signature scheme codes.
     */
    val supportedSchemes: Collection<String>,

    /**
     * Maximum number of tenants that the instance can be assigned to (null - unlimited).
     */
    val capacity: Int?,

    /**
     * Name of the CryptoServiceProvider which is used to create interface to the HSM.
     */
    val serviceName: String,
)
