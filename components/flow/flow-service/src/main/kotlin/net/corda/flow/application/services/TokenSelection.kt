package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.handlers.events.ExternalEventExecutor
import net.corda.v5.application.services.ClaimCriteria
import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.TokenSelection
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.time.Instant
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock

@Component(service = [TokenSelection::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class TokenSelection(
    private val flowFiberService: FlowFiberService,
    private val externalEventExecutor: ExternalEventExecutor,
    private val clock: Clock
) : TokenSelection, SingletonSerializeAsToken {

    @Activate
    constructor(
        @Reference(service = FlowFiberService::class)
        flowFiberService: FlowFiberService,
        @Reference(service = ExternalEventExecutor::class)
        externalEventExecutor: ExternalEventExecutor
    ) : this(flowFiberService, externalEventExecutor, UTCClock())

    @Suspendable
    override fun claim(criteria: ClaimCriteria, timeoutMilliseconds: Int): ClaimedTokens {
        return externalEventExecutor.execute(
            TokenClaimQueryExternalEventHandler::class.java,
            ClaimCriteriaRequest(
                criteria,
                true,
                Instant.ofEpochMilli(clock.instant().toEpochMilli() + timeoutMilliseconds)
            )
        )
    }

    @Suspendable
    override fun tryClaim(criteria: ClaimCriteria): ClaimedTokens {
        return externalEventExecutor.execute(
            TokenClaimQueryExternalEventHandler::class.java,
            ClaimCriteriaRequest(
                criteria,
                false,
                null
            )
        )
    }
}