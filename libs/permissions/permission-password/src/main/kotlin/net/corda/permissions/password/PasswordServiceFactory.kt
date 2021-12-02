package net.corda.permissions.password

import net.corda.permissions.password.impl.PasswordServiceImpl
import org.osgi.service.component.annotations.Component
import java.security.SecureRandom

@Component(service = [PasswordServiceFactory::class], immediate = true)
class PasswordServiceFactory {
    fun createPasswordService(secureRandom: SecureRandom): PasswordService {
        return PasswordServiceImpl(secureRandom)
    }
}