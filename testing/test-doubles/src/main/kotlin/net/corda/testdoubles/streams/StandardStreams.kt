package net.corda.testdoubles.streams

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(StandardStreamsExtension::class)
annotation class StandardStreams