package net.corda.membership.datamodel

object MembershipEntities {
    val classes = setOf(
        RegistrationRequestEntity::class.java,
        GroupPolicyEntity::class.java,
        MemberInfoEntity::class.java,
    )
}