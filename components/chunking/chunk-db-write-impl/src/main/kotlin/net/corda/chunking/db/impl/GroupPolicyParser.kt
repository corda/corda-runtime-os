package net.corda.chunking.db.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.v5.base.exceptions.CordaRuntimeException

object GroupPolicyParser {
    // Not much different from the `membership` version really.
    /**
     * Parse the group policy json string into a map
     *
     * @throws [CordaRuntimeException] if we cannot parse the map
     * @return map of values
     */
    fun parse(groupPolicyJson: String): Map<String, Any> {
        try {
            return ConfigFactory
                .parseString(groupPolicyJson)
                .root()
                .unwrapped()
        } catch (e: ConfigException.Parse) {
            throw CordaRuntimeException("Failed to parse group policy file", e)
        }
    }
}
