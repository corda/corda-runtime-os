package net.corda.rbac.schema

/**
 * A set of constants which is used for HTTP Role Based Access Control (RBAC) checks
 */
@Suppress("Unused")
object RbacKeys {

    /**
     * Prefix of the permission string which is targeting start flow operation
     */
    const val START_FLOW_PREFIX = "StartFlow"

    /**
     * Separator for permission string which sits between prefix and the wildcard expression
     */
    const val PREFIX_SEPARATOR = ":"
}