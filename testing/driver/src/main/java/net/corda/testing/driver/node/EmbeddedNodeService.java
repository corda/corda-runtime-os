package net.corda.testing.driver.node;

import net.corda.data.KeyValuePair;
import net.corda.data.virtualnode.VirtualNodeInfo;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

@DoNotImplement
public interface EmbeddedNodeService {
    /**
     * This tag is appended to the {@code commonName} field
     * of each Notary service's {@link MemberX500Name} to
     * generate the {@link MemberX500Name} for its vNode.
     */
    String NOTARY_WORKER_TAG = "(Worker)";

    /**
     * @param x500Name The {@link MemberX500Name} for a notary service.
     * @return The {@link MemberX500Name} for a notary service's virtual node.
     */
    @NotNull
    static MemberX500Name toNotaryWorkerName(@NotNull MemberX500Name x500Name) {
        requireNonNull(x500Name, "x500Name cannot be null");
        final String commonName = x500Name.getCommonName();
        return new MemberX500Name(
            (commonName == null ? "" : commonName) + NOTARY_WORKER_TAG,
            x500Name.getOrganizationUnit(),
            x500Name.getOrganization(),
            x500Name.getLocality(),
            x500Name.getState(),
            x500Name.getCountry()
        );
    }

    void configure(@NotNull Duration timeout);

    @NotNull
    @Unmodifiable
    Set<@NotNull VirtualNodeInfo> loadVirtualNodes(
        @NotNull
        @Unmodifiable
        Set<@NotNull MemberX500Name> names,

        @NotNull
        URI fileURI
    );

    void loadSystemCpi(@NotNull @Unmodifiable Set<@NotNull MemberX500Name> names, @NotNull URI fileURI);

    void setGroupParameters(@NotNull @Unmodifiable Set<@NotNull KeyValuePair> groupParameters);

    void setMembershipGroup(@NotNull @Unmodifiable Map<@NotNull MemberX500Name, @NotNull KeyPair> network);
}
