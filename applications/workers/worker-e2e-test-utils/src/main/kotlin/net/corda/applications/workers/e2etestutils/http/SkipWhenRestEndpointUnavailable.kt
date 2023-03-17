package net.corda.applications.workers.e2etestutils.http

import net.corda.applications.workers.e2etestutils.http.EndpointAvailabilityCondition
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(EndpointAvailabilityCondition::class)
annotation class SkipWhenRestEndpointUnavailable