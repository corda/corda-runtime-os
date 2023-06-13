package net.corda.testing.driver.node;

import net.corda.data.virtualnode.VirtualNodeInfo;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@DoNotImplement
public interface EmbeddedNodeService {
    void configure(@NotNull Path frameworkDirectory, @NotNull Duration timeout);

    @NotNull
    Set<VirtualNodeInfo> loadVirtualNodes(@NotNull URL fileURL);

    void setMembershipGroup(@NotNull Map<MemberX500Name, PublicKey> network);

    void setLocalIdentities(@NotNull Set<MemberX500Name> localMembers, @NotNull Map<MemberX500Name, KeyPair> localKeys);

    void configureLocalTenants(@NotNull Duration timeout);
}
