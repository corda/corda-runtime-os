package net.corda.membership

/**
 * Like network type, trust stores, etc.
 */
interface GroupPolicy : Map<String, Any> {
    val groupId: String
}
