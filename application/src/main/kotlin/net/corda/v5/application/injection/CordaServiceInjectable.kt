package net.corda.v5.application.injection

/**
 * Interfaces that extend [CordaServiceInjectable] can be injected into [net.corda.v5.application.services.CordaService]s
 * or [net.corda.v5.ledger.notary.NotaryService] using [CordaInject].
 */
interface CordaServiceInjectable