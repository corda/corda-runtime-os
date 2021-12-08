package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class HelloWorldFlowInitiator(private val jsonArg: String) : Flow<Boolean> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call() : Boolean {

        try{
            val target = jsonMarshallingService.parseJson<HelloTarget>(jsonArg)
            log.info("Hello ${target.who}!")
        }catch(e: Throwable){
            log.warn(":( could not deserialize '$jsonArg' because:'${e.message}'")
        }

        return true
    }

    class HelloTarget{
        var who:String? = null
    }
}
