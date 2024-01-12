package net.corda.membership.network.writer

import net.corda.libs.packaging.Cpi
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import javax.persistence.EntityManager

interface NetworkInfoWriter {

    /**
     * Create and persist the initial static network information for a CPI.
     * If the CPI does not contain a static network group policy file, nothing is persisted and null is returned.
     *
     * @param em The [EntityManager] used to persist the static network information from within an existing transaction.
     * @param cpi The [Cpi] from which to parse the group policy file.
     *
     * @return The persisted entity, or null if the CPI is not configured for static networks.
     */
    fun parseAndPersistStaticNetworkInfo(
        em: EntityManager,
        cpi: Cpi
    ): StaticNetworkInfoEntity?

    /**
     * Injects a static network MGM into a group policy if the group policy is a static network group policy.
     * The static network MGM details are taken from the database and are expected to be already persisted.
     *
     * @see parseAndPersistStaticNetworkInfo
     *
     * @param em The [EntityManager] used to persist the static network information from within an existing transaction.
     * @param groupPolicyJson The group policy to update and return
     *
     * @return the updated group policy string.
     */
    fun injectStaticNetworkMgm(
        em: EntityManager,
        groupPolicyJson: String
    ): String
}
