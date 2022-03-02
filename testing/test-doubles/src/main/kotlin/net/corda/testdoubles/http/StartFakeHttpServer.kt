package net.corda.testdoubles.http

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(FakeHttpServerExtension::class)
annotation class StartFakeHttpServer
