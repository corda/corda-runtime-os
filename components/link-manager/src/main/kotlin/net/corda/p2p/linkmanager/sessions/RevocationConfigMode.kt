package net.corda.p2p.linkmanager.sessions

enum class RevocationConfigMode {

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