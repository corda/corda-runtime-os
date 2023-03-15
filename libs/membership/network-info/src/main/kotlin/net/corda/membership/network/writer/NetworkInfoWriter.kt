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
     *
     */
    fun injectStaticNetworkMgm(
        em: EntityManager,
        groupPolicyJson: String
    ): String
}