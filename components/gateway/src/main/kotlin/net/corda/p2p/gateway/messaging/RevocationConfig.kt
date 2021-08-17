package net.corda.p2p.gateway.messaging

/**
 * Data structure for controlling the way how Certificate Revocation is done.
 */
interface RevocationConfig {

    enum class Mode {

        /**
         * @see java.security.cert.PKIXRevocationChecker.Option.SOFT_FAIL
         */
        SOFT_FAIL,

        /**
         * Opposite of SOFT_FAIL - i.e. most rigorous check.
         * Among other things, this check requires that CRL checking URL is available on every level of certificate chain.
         * This is also known as Strict mode.
         */
        HARD_FAIL,

        /**
         * Switch CRL check off.
         */
        OFF
    }

    val mode: Mode
}

data class RevocationConfigImpl(override val mode: RevocationConfig.Mode) : RevocationConfig