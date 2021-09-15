package net.corda.httprpc.security.read.impl

import net.corda.httprpc.security.read.AdminSubject
import net.corda.httprpc.security.read.Password
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.security.auth.login.FailedLoginException

class RPCSecurityManagerStubImplTest {

    @Test
    fun testSunnyDay() {
        val instance = RPCSecurityManagerFactoryStubImpl().get()
        "foo".let {
            val subject = instance.buildSubject(it)
            assertThat(subject.principal).isEqualTo(it)
            assertThat(subject).isInstanceOf(AdminSubject::class.java)
        }

        "admin".let {
            val subject = instance.authenticate(it, Password(it))
            assertThat(subject.principal).isEqualTo(it)
            assertThat(subject).isInstanceOf(AdminSubject::class.java)
        }
    }

    @Test
    fun testRainyDay() {
        val instance = RPCSecurityManagerFactoryStubImpl().get()
        "foo".let {
            Assertions.assertThatThrownBy { instance.authenticate(it, Password(it)) }
                .isInstanceOf(FailedLoginException::class.java)
        }
    }
}