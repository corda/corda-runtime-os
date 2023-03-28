package net.corda.membership.datamodel

object MembershipEntities {
    val vnodeClasses = setOf(
        RegistrationRequestEntity::class.java,
        GroupPolicyEntity::class.java,
        MemberInfoEntity::class.java,
        MemberSignatureEntity::class.java,
        GroupParametersEntity::class.java,
        MutualTlsAllowedClientCertificateEntity::class.java,
        PreAuthTokenEntity::class.java,
        ApprovalRulesEntity::class.java
    )

    val clusterClasses = setOf(
        StaticNetworkInfoEntity::class.java
    )
}
