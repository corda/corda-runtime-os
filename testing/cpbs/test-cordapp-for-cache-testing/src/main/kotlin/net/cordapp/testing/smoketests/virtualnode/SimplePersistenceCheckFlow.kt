package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.bundles.fish.Fish
import net.cordapp.testing.bundles.fish.Owner
import java.util.UUID

@Suppress("unused")
class SimplePersistenceCheckFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val fish = Fish(UUID.randomUUID(), "Floaty", "Black", Owner(UUID.randomUUID(), "alice", 22))
        persistenceService.persist(fish)
        return with("Could persist ${fish.name}") {
            log.info(this)
            jsonMarshallingService.format(this)
        }
    }
}
