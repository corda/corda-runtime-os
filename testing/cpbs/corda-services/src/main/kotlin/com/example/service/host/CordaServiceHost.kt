package com.example.service.host

import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE

/**
 * A component designed to receive the components found in the other CPK.
 * This cannot be an "immediate" component because none of these references
 * will exist when this bundle is first activated.
 */
@Suppress("unused")
@Component(service = [ Runnable::class ], immediate = false)
class CordaServiceHost @Activate constructor(
    @Reference(
        service = SingletonSerializeAsToken::class,
        target = CORDA_SANDBOX_FILTER,
        cardinality = MULTIPLE
    )
    private val singletons: List<SingletonSerializeAsToken>
) : Runnable {
    companion object {
        const val CORDA_SANDBOX_FILTER = "(corda.sandbox=true)"
    }

    private val logger = loggerFor<CordaServiceHost>()

    init {
        logger.info(
            "Corda Service Host: singletons={}",
            singletons
        )
    }

    @Deactivate
    fun done() {
        logger.info("Destroyed")
    }

    override fun run() {
        logger.info("Corda services validated.")
    }
}
