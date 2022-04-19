package net.corda.membership

/**
 * Object representation of the group policy file which is packaged within a CPI and provides
 * group configurations.
 */
interface GroupPolicy : Map<String, Any> {
    /**
     * Group Identifier.
     */
    val groupId: String

    /**
     * Fully qualified name of the registration implementation required for the group.
     */
    val registrationProtocol: String
}
