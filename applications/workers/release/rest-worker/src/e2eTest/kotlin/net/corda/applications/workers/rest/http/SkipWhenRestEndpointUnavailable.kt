package net.corda.applications.workers.rest.http

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(EndpointAvailabilityCondition::class)
annotation class SkipWhenRestEndpointUnavailable()