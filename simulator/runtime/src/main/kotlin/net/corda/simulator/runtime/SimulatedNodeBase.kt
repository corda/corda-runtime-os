package net.corda.simulator.runtime

import net.corda.simulator.SimulatedNode
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.util.contextLogger
import java.security.PublicKey

abstract class SimulatedNodeBase : SimulatedNode {

    companion object {
        val log = contextLogger()
    }

    protected abstract val fiber: SimFiber

    override fun getPersistenceService(): PersistenceService =
        fiber.getOrCreatePersistenceService(member)

    override fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey {
        log.info("Generating key with alias \"$alias\", hsm category \"$hsmCategory\", scheme \"$scheme\" " +
                "for member \"$member\""
        )
        return fiber.generateAndStoreKey(alias, hsmCategory, scheme, member)
    }
}
