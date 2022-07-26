package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.services.ClaimCriteria
import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.TokenSelection
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [TokenSelection::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class TokenSelection @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : TokenSelection, SingletonSerializeAsToken {

    @Suspendable
    override fun claim(criteria: ClaimCriteria, timeoutMilliseconds: Int): Pair<Boolean,ClaimedTokens?> {
        return flowFiberService.getExecutingFiber().suspend(FlowIORequest.TokenQuery(criteria,timeoutMilliseconds))
    }

    @Suspendable
    override fun tryClaim(criteria: ClaimCriteria): ClaimedTokens? {
        return flowFiberService.getExecutingFiber().suspend(FlowIORequest.TokenQuery(criteria)).second
    }
}