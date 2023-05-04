package com.r3.corda.testing.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.facade.FacadeReader
import net.corda.v5.application.interop.facade.FacadeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory


//Following protocol name is deliberately used to
// prove that interop is not using protocol string to start the responder flow
@InitiatedBy(protocol = "dummy_protocol")
class FacadeInvocationResponderFlow : ResponderFlow , SampleTokensFacade {
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var facadeReader: FacadeReader

    @CordaInject
    lateinit var facadeService: FacadeService

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(session: FlowSession) {
        log.info("FacadeInvocationResponderFlow with Weft.call() starting")
        val request = session.receive(String::class.java)
        log.info("FacadeInvocationResponderFlow with Weft.call() received : $request")

        val facade = facadeReader.read(
            """{ "id": "/com/r3/tokens/sample/v1.0",
                "commands": { 
                    "say-hello": {
                        "in": {
                            "greeting": "string"
                            },
                        "out": {
                            "greeting": "string"
                            }
                        },
                    "get-balance": {
                        "in": {
                            "greeting": "string"
                            },
                        "out": {
                            "greeting": "string"
                            }
                        }
                    }
                }""".trimIndent()
        )

        val facadeResponse = facadeService.dispatch(facade, this, request)

        //val response = facadeResponse.outParameters.first().value
        log.info("FacadeInvocationResponderFlow with Weft.call(): received=$request, response=$facadeResponse")
        session.send(facadeResponse)
    }

    override fun processHello(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("$greeting -> Bye")
    }

    override fun getBalance(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("100")
    }
}
