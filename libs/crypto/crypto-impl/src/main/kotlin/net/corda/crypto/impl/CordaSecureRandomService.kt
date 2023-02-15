package net.corda.crypto.impl

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi
import kotlin.system.exitProcess

class CordaSecureRandomService(provider: Provider) :
    Provider.Service(provider, "SecureRandom", algorithm, CordaSecureRandomSpi::javaClass.name, null, null) {

    companion object {
        const val algorithm = "CordaPRNG"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val instance: SecureRandomSpi = if (SystemUtils.IS_OS_LINUX) tryAndUseLinuxSecureRandomSpi() else CordaSecureRandomSpi()

    @Suppress("TooGenericExceptionThrown")
    private fun tryAndUseLinuxSecureRandomSpi(): SecureRandomSpi = try {
        LinuxSecureRandomSpi()
    } catch (e: Throwable) {
        logger.error("Unable to initialise LinuxSecureRandomSpi. The process will now exit.", e)
        exitProcess(1)
    }

    override fun newInstance(constructorParameter: Any?) = instance
}

private class CordaSecureRandomSpi : SecureRandomSpi() {
    private val threadLocalSecureRandom = object : ThreadLocal<SecureRandom>() {
        override fun initialValue() = SecureRandom.getInstanceStrong()
    }

    private val secureRandom: SecureRandom get() = threadLocalSecureRandom.get()

    override fun engineSetSeed(seed: ByteArray) = secureRandom.setSeed(seed)
    override fun engineNextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
    override fun engineGenerateSeed(numBytes: Int): ByteArray = secureRandom.generateSeed(numBytes)
}

@Suppress("TooGenericExceptionThrown")
private class LinuxSecureRandomSpi : SecureRandomSpi() {
    private fun openURandom(): InputStream {
        try {
            val file = File("/dev/urandom")
            val stream = FileInputStream(file)
            if (stream.read() == -1)
                throw RuntimeException("/dev/urandom not readable?")
            return stream
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    private var urandom = DataInputStream(openURandom())

    override fun engineSetSeed(seed: ByteArray) {}
    override fun engineNextBytes(bytes: ByteArray) = try {
        urandom.readFully(bytes)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }

    override fun engineGenerateSeed(numBytes: Int): ByteArray = ByteArray(numBytes).apply { engineNextBytes(this) }
}
