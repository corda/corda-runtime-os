package net.corda.v5.application.injection

import kotlin.annotation.AnnotationTarget.FIELD

/**
 * This annotation can be used with [Flow] and [CordaService] to indicate which dependencies should be injected.
 */
@Target(FIELD)
@MustBeDocumented
annotation class CordaInject

/**
 * This annotation can be used with [CordaService] to indicate which dependencies should be injected before starting
 * the service.
 * Combined with the corda service lifecycle support, this allows starting a service to depend on another service. It
 * is not be possible to inject dependencies for use within a service constructor, so in order to use a service to start
 * another service it must be injected before the service start lifecycle event is distributed using this annotation.
 */
@Target(FIELD)
@MustBeDocumented
annotation class CordaInjectPreStart