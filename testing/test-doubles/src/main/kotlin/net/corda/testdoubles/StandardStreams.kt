package net.corda.testdoubles

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(StandardStreamsExtension::class)
annotation class StandardStreams