package net.corda.install

/** Thrown if an exception occurs while installing drivers. */
class DriverInstallationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown if an exception occurs while installing CorDapps. */
class CpkInstallationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown if an exception occurs while verifying unpacked CPKs. */
class CpkVerificationException(message: String, cause: Throwable? = null): RuntimeException(message, cause)