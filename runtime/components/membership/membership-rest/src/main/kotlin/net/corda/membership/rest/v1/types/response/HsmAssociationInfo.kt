package net.corda.membership.rest.v1.types.response


/**
 * Information on a hardware security module (HSM)
 */
data class HsmAssociationInfo(
    /**
     * The configuration ID
     */
    val id: String,

    /**
     * The assigned HSM id
     */
    val hsmId: String,

    /**
     * The category
     */
    val category: String,

    /**
     * If defined then this is the master key alias which is used
     */
    val masterKeyAlias: String?,

    /**
     * Time when the association was deprecated, epoch time in seconds, 0 means the active association
     */
    val deprecatedAt: Long
)

