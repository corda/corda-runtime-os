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
        @NotNull Map<MemberX500Name, KeyPair> members,
        @NotNull Map<MemberX500Name, KeyPair> notaries,
        @NotNull Set<KeyValuePair> groupParameters
    ) {
        super(members, notaries, groupParameters);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        createDriver(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        destroyDriver(context);
    }
}
