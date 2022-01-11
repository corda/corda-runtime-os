package net.corda.crypto.impl.serializer

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
@Component(service = [InternalCustomSerializer::class])
class PublicKeySerializer : BaseDirectSerializer<PublicKey>() {

    @Reference(service = CipherSuiteFactory::class)
    lateinit var factory: CipherSuiteFactory

    override val type: Class<PublicKey> get() = PublicKey::class.java
    override val withInheritance: Boolean get() = true

    override fun writeObject(obj: PublicKey, writer: WriteObject)
        = writer.putAsBytes(factory.getSchemeMap().encodeAsByteArray(obj))

    override fun readObject(reader: ReadObject): PublicKey
        = factory.getSchemeMap().decodePublicKey(reader.getAsBytes())
}
