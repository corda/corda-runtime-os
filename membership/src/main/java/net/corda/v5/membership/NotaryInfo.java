package net.corda.v5.membership;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Collection;

/**
 * <p>Stores information about a notary service available in the network.</p>
 *
 * <p>Example usages:</p>
 *
 * <ul>
 * <li>Java:<pre>{@code
 * MemberX500Name name = notaryInfo.getName();
 * String protocol = notaryInfo.getProtocol();
 * Collection<Integer> protocolVersions = notaryInfo.getProtocolVersions();
 * PublicKey publicKey = notaryInfo.getPublicKey();
 * }</pre></li>
 * <li>Kotlin:<pre>{@code
 * val name = notaryInfo.name
 * val protocol = notaryInfo.protocol
 * val protocolVersions = notaryInfo.protocolVersions
 * val publicKey = notaryInfo.publicKey
 * }</pre></li>
 * </ul>
 */
@CordaSerializable
public interface NotaryInfo {
    /**
     * @return Name of the notary (note that it can be an identity of the distributed node).
     */
    @NotNull MemberX500Name getName();

    /**
     * @return The name of the flow protocol used by this notary.
     */
    @NotNull String getProtocol();

    /**
     * @return List of versions supported for the flow protocol used by this notary.
     */
    @NotNull Collection<Integer> getProtocolVersions();

    /**
     * @return The public key of the notary service, which will be a composite key of all notary virtual nodes keys.
     */
    @NotNull PublicKey getPublicKey();
}
