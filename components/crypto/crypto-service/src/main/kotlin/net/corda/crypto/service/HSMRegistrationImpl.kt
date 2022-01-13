package net.corda.crypto.service

import org.osgi.service.component.annotations.Component

@Component(service = [HSMRegistration::class])
class HSMRegistrationImpl : HSMRegistration {
}