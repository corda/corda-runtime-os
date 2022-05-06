package net.corda.crypto.client

import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.Lifecycle

/**
 * The HSM configuration client.
 */
interface HSMConfigurationClient : Lifecycle {
    /**
     * Adds a new or updates existing HSM configuration. The empty info.id indicates that it's a new configuration.
     *
     * @return The configuration's id.
     */
    fun putHSM(info: HSMInfo, serviceConfig: ByteArray): String

    /**
     * Replaces or add the category links to HSM configuration.
     */
    fun linkCategories(configId: String, links: List<HSMCategoryInfo>)

    /**
     * Returns all configured HSMs.
     *
     * @param filter the optional map of the filter parameters such as
     * category (the HSM's category which handles the keys)
     */
    fun lookup(filter: Map<String, String>): List<HSMInfo>

    /**
     * Return list of linked categories for the given HSM configuration.
     */
    fun getLinkedCategories(configId: String): List<HSMCategoryInfo>

}