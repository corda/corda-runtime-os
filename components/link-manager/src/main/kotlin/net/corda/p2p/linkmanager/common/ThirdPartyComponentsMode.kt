package net.corda.p2p.linkmanager.common

/**
 * This switch will exist temporarily until we complete migration with all the third-party components (e.g. membership/crypto clients).
 * After that point, it can be removed as only the real components will be used.
 */
enum class ThirdPartyComponentsMode {
    /**
     * In this mode, the real component is used.
     */
    REAL,

    /**
     * In this mode, the internal (stubbed) component is used.
     */
    //STUB
}
