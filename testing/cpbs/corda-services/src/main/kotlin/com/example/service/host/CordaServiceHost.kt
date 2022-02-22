package com.example.service.host

import com.example.service.ComponentOneCordaService
import com.example.service.ComponentTwoCordaService
import com.example.service.PojoCordaService
import net.corda.v5.application.services.CordaService
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL

/**
 * A component designed to receive the components found in the other CPK.
 * This cannot be an "immediate" component because none of these references
 * will exist when this bundle is first activated.
 */
@Suppress("unused")
@Component(service = [ Runnable::class ], immediate = false)
class CordaServiceHost @Activate constructor(
    @Reference(
        target = CORDA_SANDBOX_FILTER,
        cardinality = OPTIONAL
    )
    private val service1: ComponentOneCordaService?,
    @Reference(
        target = CORDA_SANDBOX_FILTER,
        cardinality = OPTIONAL
    )
    private val service2: ComponentTwoCordaService?,
    @Reference(
        target = CORDA_SANDBOX_FILTER,
        cardinality = OPTIONAL
    )
    private val service3: PojoCordaService?,
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
            "Corda Service Host: service1={}, service2={}, service3={}, singletons={}",
            service1, service2, service3, singletons
        )
    }

    @Deactivate
    fun done() {
        logger.info("Destroyed")
    }

    private fun validate(service: CordaService?) {
        require(service != null) {
            "Service missing!?"
        }
        require(service in singletons) {
            "Service $service not found in $singletons"
        }
    }

    override fun run() {
        validate(service1)
        validate(service2)
        validate(service3)
        logger.info("Corda services validated.")
    }
}
