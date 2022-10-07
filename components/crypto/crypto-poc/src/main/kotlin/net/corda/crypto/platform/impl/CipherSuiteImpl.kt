package net.corda.crypto.platform.impl

import net.corda.v5.cipher.suite.CipherSuite
import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.handlers.CipherSuiteRegistrar
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [CipherSuite::class, CipherSuiteBase::class])
class CipherSuiteImpl @Activate constructor(
    @Reference(
        service = CipherSuiteRegistrar::class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    registrars: List<CipherSuiteRegistrar>
) : CipherSuiteBaseImpl(), CipherSuite {

    init {
        registrars.forEach {
            it.registerWith(this)
        }
    }

    override fun register(
        keyScheme: KeySchemeInfo,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?
    ) {
        if(encodingHandler == null && verifyHandler == null) {
            return
        }
        add(keyScheme)
        if (encodingHandler != null) {
            add(keyScheme.scheme, encodingHandler)
        }
        if (verifyHandler != null) {
            add(keyScheme.scheme, verifyHandler)
        }
    }
}