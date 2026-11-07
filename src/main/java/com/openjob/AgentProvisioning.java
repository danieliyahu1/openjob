package com.openjob;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Creates a user's agent and workspace in the gateway.
 *
 * <p>Signup calls this before the account row exists, so a created account
 * always has a working agent. Later calls are free: a slug that already
 * succeeded in this process is not sent to the sidecar again, and that is why
 * the chat and automations paths do not pay a network round-trip per request.
 *
 * <p>The memory is per process on purpose. After a restart the first call per
 * account reaches the sidecar once more; it is idempotent and returns quickly
 * when the agent is already there.
 */
@Component
class AgentProvisioning {

    private final ProvisionerClient provisioner;
    private final Set<String> ready = ConcurrentHashMap.newKeySet();

    AgentProvisioning(ProvisionerClient provisioner) {
        this.provisioner = provisioner;
    }

    /**
     * Creates the agent and workspace when they are missing.
     *
     * @throws AgentUnavailable when the sidecar cannot be reached or refuses;
     *         the caller decides what that means for its own request
     */
    void ensure(String slug) {
        if (ready.contains(slug)) {
            return;
        }
        try {
            provisioner.ensure(slug);
        } catch (RuntimeException failure) {
            throw new AgentUnavailable(slug, failure);
        }
        ready.add(slug);
    }

    /** The agent is missing and could not be created. */
    static class AgentUnavailable extends RuntimeException {

        private final String slug;

        AgentUnavailable(String slug, Throwable cause) {
            super("could not provision agent " + slug, cause);
            this.slug = slug;
        }

        String slug() {
            return slug;
        }
    }
}
