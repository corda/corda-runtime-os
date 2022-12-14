package net.corda.libs.cpiupload.endpoints.v1

import java.time.Instant

/**
 * NOTE:
 * This class is visible to end-users via JSON serialization,
 * so the variable names are significant, and should be consistent
 * anywhere they are used as parameter inputs.
 */
/**
 * CPK Metadata
 *
 * @param id CPK identifier.
 * @param mainBundle Name for the CPK's main bundle.
 * @param libraries List of library dependencies.
 * @param dependencies List of CPKs that are a dependency.
 * @param type Type of CPK (for example, contract, workflow).
 * @param hash File hash of the CPK.
 * @param timestamp Timestamp indicating when the CPK was uploaded.
 */
data class CpkMetadata(
    val id : CpkIdentifier,
    val mainBundle : String,
    val libraries : List<String>,
    val type : String,
    val hash: String,
    val timestamp: Instant
)