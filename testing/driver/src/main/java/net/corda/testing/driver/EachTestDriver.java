package net.corda.testing.driver;

import java.security.KeyPair;
import java.util.Map;
import java.util.Set;
import net.corda.data.KeyValuePair;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class EachTestDriver extends AbstractDriver implements BeforeEachCallback, AfterEachCallback {
    EachTestDriver(
        @NotNull Map<@NotNull MemberX500Name, @NotNull KeyPair> members,
        @NotNull Map<@NotNull MemberX500Name, @NotNull KeyPair> notaries,
        @NotNull Set<@NotNull KeyValuePair> groupParameters
    ) {
        super(members, notaries, groupParameters);
    }

    @Override
    public void beforeEach(@NotNull ExtensionContext context) {
        createDriver(context);
    }

    @Override
    public void afterEach(@NotNull ExtensionContext context) throws Exception {
        destroyDriver(context);
    }
}
