package net.corda.testing.driver.node;

import net.corda.data.KeyValuePair;
import net.corda.data.virtualnode.VirtualNodeInfo;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@DoNotImplement
public interface EmbeddedNodeService {
    void configure(@NotNull Duration timeout);

    @NotNull
    Set<VirtualNodeInfo> loadVirtualNodes(@NotNull Set<MemberX500Name> names, @NotNull URI fileURI);

    void loadSystemCpi(@NotNull Set<MemberX500Name> names, @NotNull URI fileURI);

    void setGroupParameters(@NotNull Set<KeyValuePair> groupParameters);

    void setMembershipGroup(@NotNull Map<MemberX500Name, KeyPair> network);
}
