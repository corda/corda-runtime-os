package net.corda.httprpc.client.auth.credentials;

import org.jetbrains.annotations.NotNull;

/**
 * Interface to provide a Bearer token to HTTP RPC API calls
 */
public interface BearerTokenProvider {
    @NotNull
    String getToken();
}
