package net.corda.application.impl.flow.flowservices

import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.v5.application.flows.flowservices.FlowIdentity
import net.corda.v5.application.identity.AnonymousParty
import net.corda.v5.application.identity.Party
import net.corda.v5.application.identity.PartyAndReference
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey


@Component(service = [FlowIdentity::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class FlowIdentityImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowIdentity, SingletonSerializeAsToken, CordaFlowInjectable {

    /**
     * TODOs:
     * This is a dummy implementation for now, this needs to be modified once we
     * completed the full identity management
     */
    override val ourIdentity
        get() = TempPartyImpl(
            MemberX500Name.parse(flowFiberService.getExecutingFiber().getExecutionContext().holdingIdentity.x500Name),
            NullPublicKey()
        )

    class NullPublicKey : PublicKey {
        override fun getAlgorithm() = ""
        override fun getFormat() = ""
        override fun getEncoded() = byteArrayOf()
    }

    class TempAnonymousPartyImpl(override val owningKey: PublicKey) : AnonymousParty {
        override fun nameOrNull(): MemberX500Name? = null
        override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    }

    class TempPartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party {
        override fun nameOrNull(): MemberX500Name = name
        override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
        override fun anonymise(): AnonymousParty = TempAnonymousPartyImpl(owningKey)
    }
}