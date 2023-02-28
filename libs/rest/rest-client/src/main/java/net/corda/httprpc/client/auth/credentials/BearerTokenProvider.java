package net.corda.rest.client.auth.credentials;

import org.jetbrains.annotations.NotNull;

/**
 * Interface to provide a Bearer token to REST API calls
 */
public interface BearerTokenProvider {
    @NotNull
    String getToken();
}
