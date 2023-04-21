package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalDirectSerializer
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

class AlgorithmParameterSpecSerializer : BaseDirectSerializer<AlgorithmParameterSpec>() {
    override fun readObject(reader: InternalDirectSerializer.ReadObject): AlgorithmParameterSpec {
        return reader.getAs(AlgorithmParameterSpec::class.java)
    }

    override fun writeObject(obj: AlgorithmParameterSpec, writer: InternalDirectSerializer.WriteObject) {
        writer.putAsObject(obj)
    }

    override val type: Class<AlgorithmParameterSpec> = AlgorithmParameterSpec::class.java
    override val withInheritance = false
}

class PSSParameterSpecSerializer : BaseProxySerializer<PSSParameterSpec, PSSParameterSpecSerializer.PSSParameterSpecProxy>() {
    override val type: Class<PSSParameterSpec> = PSSParameterSpec::class.java
    override val withInheritance: Boolean = true
    override val revealSubclasses: Boolean = true
    override val proxyType: Class<PSSParameterSpecProxy> = PSSParameterSpecProxy::class.java

    override fun toProxy(obj: PSSParameterSpec): PSSParameterSpecProxy =
        PSSParameterSpecProxy(
            obj.digestAlgorithm,
            obj.mgfAlgorithm,
            obj.mgfParameters,
            obj.saltLength,
            obj.trailerField
        )

    override fun fromProxy(proxy: PSSParameterSpecProxy): PSSParameterSpec =
        PSSParameterSpec(
            proxy.mdName,
            proxy.mgfName,
            proxy.mgfSpec,
            proxy.saltLen,
            proxy.trailerField
        )

    data class PSSParameterSpecProxy(
        val mdName: String,
        val mgfName: String,
        val mgfSpec: AlgorithmParameterSpec,
        val saltLen: Int,
        val trailerField: Int
    )
}

class MGF1ParameterSpecSerializer : BaseDirectSerializer<MGF1ParameterSpec>() {
    override val type: Class<MGF1ParameterSpec> = MGF1ParameterSpec::class.java
    override val withInheritance: Boolean = false

    override fun readObject(reader: InternalDirectSerializer.ReadObject): MGF1ParameterSpec =
        MGF1ParameterSpec(reader.getAs(String::class.java))

    override fun writeObject(obj: MGF1ParameterSpec, writer: InternalDirectSerializer.WriteObject) {
        writer.putAsString(obj.digestAlgorithm)
    }
}