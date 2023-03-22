package net.corda.membership.datamodel

object MembershipEntities {
    val classes = setOf(
        RegistrationRequestEntity::class.java,
        GroupPolicyEntity::class.java,
        MemberInfoEntity::class.java,
        GroupParametersEntity::class.java,
        MutualTlsAllowedClientCertificateEntity::class.java,
        PreAuthTokenEntity::class.java,
        ApprovalRulesEntity::class.java
    )
}
