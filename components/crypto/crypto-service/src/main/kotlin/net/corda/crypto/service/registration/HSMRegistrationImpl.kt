package net.corda.crypto.service.registration

import org.osgi.service.component.annotations.Component

@Component(service = [HSMRegistration::class])
class HSMRegistrationImpl : HSMRegistration {
}