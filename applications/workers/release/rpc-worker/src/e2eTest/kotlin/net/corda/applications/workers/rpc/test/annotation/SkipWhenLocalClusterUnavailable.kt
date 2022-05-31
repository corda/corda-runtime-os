package net.corda.applications.workers.rpc.test.annotation

import org.junit.jupiter.api.extension.ExtendWith


@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(EndpointAvailabilityCondition::class)
annotation class SkipWhenLocalClusterUnavailable()