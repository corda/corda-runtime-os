@file:Suppress("unused")
package com.example.service

import net.corda.v5.application.services.CordaService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.BUNDLE
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ ComponentOneCordaService::class ])
class ComponentOneCordaService : CordaService

@Component(service = [ ComponentTwoCordaService::class, CordaService::class ])
class ComponentTwoCordaService : CordaService

@Component(enabled = false, service = [ DisabledComponentCordaService::class ])
class DisabledComponentCordaService : CordaService

@Component(service = [ CordaService::class ])
class MisconfiguredComponentCordaService : CordaService

@Component(service = [ BundleComponentCordaService::class ], scope = BUNDLE)
class BundleComponentCordaService : CordaService

@Component(service = [ PrototypeComponentCordaService::class ], scope = PROTOTYPE)
class PrototypeComponentCordaService : CordaService

@Component(service = [ SingletonComponentService::class ])
class SingletonComponentService : SingletonSerializeAsToken

class PojoCordaService : CordaService

class PojoWithArgsCordaService(val data: String) : CordaService

class SingletonPojoService : SingletonSerializeAsToken
