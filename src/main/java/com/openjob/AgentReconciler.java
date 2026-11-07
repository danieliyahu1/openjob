package com.openjob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-registers the accounts' agents after a gateway restart.
 *
 * <p>Agent entries are written to the gateway's openclaw.json, which the image
 * replaces on every pod start. The persisted scheduler may still hold jobs for
 * those agents, so adding the entries back keeps automations from pointing at
 * an agent the gateway no longer knows.
 */
@Component
class AgentReconciler {

    private static final Logger log = LoggerFactory.getLogger(AgentReconciler.class);

    private final UserAgentStore agents;
    private final ProvisionerClient provisioner;

    AgentReconciler(UserAgentStore agents, ProvisionerClient provisioner) {
        this.agents = agents;
        this.provisioner = provisioner;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    void reconcile() {
        int attempted = 0;
        int provisioned = 0;
        for (String slug : agents.slugs()) {
            attempted++;
            try {
                provisioner.ensure(slug);
                provisioned++;
            } catch (RuntimeException notReady) {
                log.debug("agent_reconcile_skipped slug={} error={}",
                        slug, notReady.getClass().getSimpleName());
            }
        }
        if (attempted == 0) {
            return;
        }
        if (provisioned == attempted) {
            log.debug("agent_reconcile attempted={} provisioned={}", attempted, provisioned);
        } else {
            log.warn("agent_reconcile attempted={} provisioned={}", attempted, provisioned);
        }
    }
}
