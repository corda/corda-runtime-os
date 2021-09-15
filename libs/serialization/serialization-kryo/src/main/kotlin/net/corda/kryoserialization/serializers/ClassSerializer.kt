package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.classinfo.ClassInfoException
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.resolver.SandboxClassResolver
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.BasicHashingService
import net.corda.v5.crypto.SecureHash

class ClassSerializer(
    private val classInfoService: ClassInfoService,
    private val sandboxGroup: SandboxGroup,
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
            val signerSummaryHash = SecureHash.create(input.readString())
            val cpk = Cpk.ShortIdentifier(cpkName, version, signerSummaryHash)
            sandboxGroup.loadClassFromCordappBundle(cpk, input.readString())
        }
    }

    override fun write(kryo: Kryo, output: Output, clazz: Class<*>) {
        val classInfo = try {
            classInfoService.getClassInfo(clazz)
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
            output.writeString(classInfo.cpkSignerSummaryHash.toString())
        } else {
            output.writeString(NO_CPK_NAME)
        }
        output.writeString(clazz.name)
    }
}

