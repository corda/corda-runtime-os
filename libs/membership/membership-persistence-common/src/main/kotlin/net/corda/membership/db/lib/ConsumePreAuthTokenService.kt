package net.corda.membership.db.lib

import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class ConsumePreAuthTokenService(
    private val clock: Clock,
) {
    @Suppress("ThrowsCount")
    fun consume(
        em: EntityManager,
        ownerX500Name: MemberX500Name,
        tokenId: String,
    ) {
        val requestReceived = clock.instant()
        val token = em.find(
            PreAuthTokenEntity::class.java,
            tokenId,
            LockModeType.PESSIMISTIC_WRITE,
        ) ?: throw MembershipPersistenceException("Pre-auth token '$tokenId' does not exist.")
        if (MemberX500Name.parse(token.ownerX500Name) != ownerX500Name) {
            throw MembershipPersistenceException(
                "Pre-auth token '$tokenId' does not exist for " +
                    "$ownerX500Name.",
            )
        }
        token.ttl?.run {
            if (this <= requestReceived) {
                throw MembershipPersistenceException("Pre-auth token '$tokenId' expired at $this")
            }
        }
        if (token.status.lowercase() != PreAuthTokenStatus.AVAILABLE.toString().lowercase()) {
            throw MembershipPersistenceException(
                "Pre-auth token '$tokenId' is not in " +
                    "${PreAuthTokenStatus.AVAILABLE} status. Status is ${token.status}",
            )
        }

        token.status = PreAuthTokenStatus.CONSUMED.toString()
        token.removalRemark = "Token consumed at $requestReceived"
        em.merge(token)
    }
}
