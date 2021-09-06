package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.factories.ReflectionSerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import net.corda.classinfo.ClassInfoException
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.osgi.SandboxClassResolver
import net.corda.kryoserialization.readBytesWithLength
import net.corda.kryoserialization.writeBytesWithLength
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.internal.uncheckedCast
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.BasicHashingService
import net.corda.v5.crypto.SecureHash
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.TreeSet

// This file contains Serializers for types which are part of the Kotlin and Java Platform.

internal class ClassSerializer(
    val classInfoService: Any?,
    val sandboxGroup: Any?,
    private val hashingService: BasicHashingService
) : Serializer<Class<*>>() {
    companion object {
        private const val NO_CPK_NAME = "No CPK"
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Class<*>>): Class<*> {
        val cpkName = input.readString()
        return if (cpkName == NO_CPK_NAME) {
            val name = input.readString()
            Class.forName(name, true, kryo.classLoader)
        } else {
            val version = input.readString()
            var numberOfSigners = input.readVarInt(true)
            val signers = TreeSet<SecureHash>()
            while (numberOfSigners > 0) {
                signers.add(hashingService.create(input.readString()))
                numberOfSigners--
            }
            val cpk = Cpk.Identifier(cpkName, version, signers)
            (sandboxGroup as SandboxGroup).loadClass(cpk, input.readString())
        }
    }

    override fun write(kryo: Kryo, output: Output, clazz: Class<*>) {
        val classInfo = try {
            (classInfoService as ClassInfoService).getClassInfo(clazz)
        } catch (ex: ClassInfoException) {
            SandboxClassResolver.logger.trace {
                "Class ${clazz.name} not found in sandbox. Possibly a platform class. ${ex.message}"
            }
            null
        }

        if (classInfo is CpkClassInfo) {
            output.writeString(classInfo.classBundleName)
            output.writeString(classInfo.classBundleVersion.toString())
            output.writeString(classInfo.cordappBundleName)
            output.writeString(classInfo.cordappBundleVersion.toString())
            output.writeVarInt(classInfo.cpkPublicKeyHashes.size, true)
            classInfo.cpkPublicKeyHashes.forEach {
                output.writeString(it.toString())
            }
        } else {
            output.writeString(NO_CPK_NAME)
        }
        output.writeString(clazz.name)
    }
}

internal object CertPathSerializer : Serializer<CertPath>() {
    override fun read(kryo: Kryo, input: Input, type: Class<CertPath>): CertPath {
        val factory = CertificateFactory.getInstance(input.readString())
        return factory.generateCertPath(input.readBytesWithLength().inputStream())
    }

    override fun write(kryo: Kryo, output: Output, obj: CertPath) {
        output.writeString(obj.type)
        output.writeBytesWithLength(obj.encoded)
    }
}

internal object X509CertificateSerializer : Serializer<X509Certificate>() {
    override fun read(kryo: Kryo, input: Input, type: Class<X509Certificate>): X509Certificate {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(input.readBytesWithLength().inputStream()) as X509Certificate
    }

    override fun write(kryo: Kryo, output: Output, obj: X509Certificate) {
        output.writeBytesWithLength(obj.encoded)
    }
}

/**
 * For serializing instances if [Throwable] honoring the fact that [java.lang.Throwable.suppressedExceptions]
 * might be un-initialized/empty.
 * In the absence of this class [CompatibleFieldSerializer] will be used which will assign a *new* instance of
 * unmodifiable collection to [java.lang.Throwable.suppressedExceptions] which will fail some sentinel identity checks
 * e.g. in [java.lang.Throwable.addSuppressed]
 */
class ThrowableSerializer<T>(kryo: Kryo, type: Class<T>) : Serializer<Throwable>(false, true) {

    private companion object {
        private val IS_OPENJ9 = System.getProperty("java.vm.name").toLowerCase().contains("openj9")
        private val suppressedField = Throwable::class.java.getDeclaredField("suppressedExceptions")

        private val sentinelValue = let {
            if (!IS_OPENJ9) {
                val sentinelField = Throwable::class.java.getDeclaredField("SUPPRESSED_SENTINEL")
                sentinelField.isAccessible = true
                sentinelField.get(null)
            } else {
                Collections.EMPTY_LIST
            }
        }

        init {
            suppressedField.isAccessible = true
        }
    }

    private val delegate: Serializer<Throwable> =
        uncheckedCast(ReflectionSerializerFactory.makeSerializer(kryo, FieldSerializer::class.java, type))

    override fun write(kryo: Kryo, output: Output, throwable: Throwable) {
        delegate.write(kryo, output, throwable)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Throwable>): Throwable {
        val throwableRead = delegate.read(kryo, input, type)
        if (throwableRead.suppressed.isEmpty()) {
            throwableRead.setSuppressedToSentinel()
        }
        return throwableRead
    }

    private fun Throwable.setSuppressedToSentinel() = suppressedField.set(this, sentinelValue)
}