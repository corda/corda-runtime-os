package net.corda.kotlin.test.example

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS

@Retention(RUNTIME)
@Target(CLASS)
annotation class SampleAnnotation(val value: String)
