package net.corda.kryoserialization.osgi

import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Registration
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.IdentityObjectIntMap
import com.esotericsoftware.kryo.util.IntMap
import net.corda.classinfo.ClassInfoException
import net.corda.classinfo.ClassInfoService
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.BasicHashingService
import net.corda.v5.crypto.SecureHash
import java.util.TreeSet

open class SandboxClassResolver(
    val classInfoService: Any?,
    val sandboxGroup: Any?,
    private val hashingService: BasicHashingService
) : DefaultClassResolver() {

    companion object {
        val logger = contextLogger()
    }

    private var cpkToId: IdentityObjectIntMap<Cpk.Identifier>? = null

    private var idToCpk: IntMap<Cpk.Identifier>? = null

    private val noCpkId = 0

    private var nextCpkId = noCpkId + 1

    /**
     * Identifies the CPK identified associated with a certain class.
     *
     * @param [klass] The class to get a [Cpk.Identifier] for.
     *
     * @return The [Cpk.Identifier] associated with [klass] if it is a CPK class info, or null otherwise.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun getCpkFromClass(klass: Class<*>): Cpk.Identifier? {
        val classInfo = try {
            (classInfoService as ClassInfoService).getClassInfo(klass)
        } catch (ex: ClassInfoException) {
            logger.trace { "Class ${klass.name} not found in sandbox. Possibly a platform class. ${ex.message}" }
            null
        } catch (ex: NullPointerException) {
            logger.trace { "This is likely a unit test with mocked objects. ${ex.message}" }
            null
        }

        return when (classInfo) {
            is CpkClassInfo -> Cpk.Identifier(
                    classInfo.classBundleName,
                    classInfo.classBundleVersion.toString(),
                    TreeSet(classInfo.cpkPublicKeyHashes))
            else -> null
        }
    }

    /**
     * Auxiliary method for initialising two maps for keeping track of
     * CPKs to ids and Class names to ids.
     */
    private fun checkAndInitWriteStructures() {
        if (cpkToId == null) cpkToId = IdentityObjectIntMap<Cpk.Identifier>()
        if (classToNameId == null) classToNameId = IdentityObjectIntMap<Class<*>>()
    }

    /**
     * Overwrites internal [writeName] function to allow for capturing CPK metadata
     * in addition to the [type] class name.
     *
     * @param [output] an output stream for writing the serialised data.
     * @param [type] the type to be serialised.
     * @param [registration] a registry of serializers.
     */
    override fun writeName(output: Output, type: Class<*>, registration: Registration) {
        checkAndInitWriteStructures()
        output.writeVarInt(NAME + 2, true)

        // If we have seen this type before, then write its id
        var nameId = classToNameId[type, -1]
        if (nameId != -1) {
            output.writeVarInt(nameId, true)
            return
        }

        // Only write the class name the first time encountered in object graph.
        nameId = nextNameId++
        classToNameId.put(type, nameId)
        output.writeVarInt(nameId, true)
        output.writeString(type.name)

        // If the type is a PlatformClassInfo, write noCpkId
        val cpk = getCpkFromClass(type)
        if (cpk == null) {
            output.writeVarInt(noCpkId, true)
            return
        }

        val checkedCpkToId = checkNotNull(cpkToId) { "cpkToId should not be null." }
        var cpkId = checkedCpkToId[cpk, -1]
        if (cpkId == -1) {
            cpkId = nextCpkId++
            checkedCpkToId.put(cpk, cpkId)
            output.writeVarInt(cpkId, true)
            writeCpkIdentifier(output, cpk)
        } else {
            output.writeVarInt(cpkId, true)
        }
    }

    private fun writeCpkIdentifier(output: Output, identifier: Cpk.Identifier) {
        output.writeString(identifier.symbolicName)
        output.writeString(identifier.version)
        output.writeString(identifier.signers.size.toString())
        identifier.signers.forEach {
            output.writeString(it.toString())
        }
    }

    private fun checkAndInitReadStructures() {
        if (idToCpk == null) idToCpk = IntMap<Cpk.Identifier>()
        if (nameIdToClass == null) nameIdToClass = IntMap<Class<*>>()
    }

    /**
     * Overwrites internal [readName] function to allow for identifying CPK metadata
     * associated with the [type] class name.
     *
     * @param [input] an input stream for reading the serialised data.
     *
     * @return [registration] a registry of serializers.
     */
    override fun readName(input: Input): Registration {
        checkAndInitReadStructures()
        val nameId = input.readVarInt(true)
        var type = nameIdToClass[nameId]
        if (type == null) {
            // Only read the class name the first time encountered in object graph.
            val className = input.readString()
            val cpkId = input.readVarInt(true)
            if (cpkId == noCpkId) {
                try {
                    type = Class.forName(className, false, kryo.classLoader)
                } catch (ex: ClassNotFoundException) {
                    throw KryoException("Unable to find class: $className in default classloader (not in a Sandbox).")
                }
                nameIdToClass.put(nameId, type)
                return kryo.getRegistration(type)
            }

            val cpkIdentifier = readCpkIdentifier(input)

            type = try {
                (sandboxGroup as SandboxGroup).loadClass(cpkIdentifier, className)
            }   catch (ex: SandboxException) {
                Class.forName(className, false, kryo.classLoader)
            } catch (ex: ClassNotFoundException) {
                throw KryoException("Unable to find class: " + className + " in CPK: " + cpkIdentifier.symbolicName, ex)
            }

            nameIdToClass.put(nameId, type)
        }
        return kryo.getRegistration(type)
    }

    private fun readCpkIdentifier(input: Input): Cpk.Identifier {
        val symbolicName = input.readString()
        val version = input.readString()
        var numberOfSigners = Integer.parseInt(input.readString())
        val signers = TreeSet<SecureHash>()
        while (numberOfSigners > 0) {
            signers.add(hashingService.create(input.readString()))
            numberOfSigners--
        }
        return Cpk.Identifier(symbolicName, version, signers)
    }

    override fun reset() {
        super.reset()
        if (!kryo.isRegistrationRequired) {
            idToCpk?.clear()
            cpkToId?.clear()
            nextCpkId = noCpkId + 1
        }
    }
}