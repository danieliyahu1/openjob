package com.openjob;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class AgentProvisioningTest {

    private final ProvisionerClient provisioner = mock(ProvisionerClient.class);
    private final AgentProvisioning provisioning = new AgentProvisioning(provisioner);

    @Test
    void asksTheSidecarOnlyOncePerAgent() {
        provisioning.ensure("u-0123456789ab");
        provisioning.ensure("u-0123456789ab");

        verify(provisioner, times(1)).ensure("u-0123456789ab");
    }

    @Test
    void keepsAccountsApart() {
        provisioning.ensure("u-0123456789ab");
        provisioning.ensure("u-ffffffffffff");

        verify(provisioner).ensure("u-0123456789ab");
        verify(provisioner).ensure("u-ffffffffffff");
    }

    @Test
    void retriesAfterAFailureInsteadOfCachingIt() {
        doThrow(new IllegalStateException("sidecar down"))
                .doNothing()
                .when(provisioner).ensure(anyString());

        assertThatThrownBy(() -> provisioning.ensure("u-0123456789ab"))
                .isInstanceOf(AgentProvisioning.AgentUnavailable.class)
                .satisfies(problem -> org.assertj.core.api.Assertions
                        .assertThat(((AgentProvisioning.AgentUnavailable) problem).slug())
                        .isEqualTo("u-0123456789ab"));

        assertThatCode(() -> provisioning.ensure("u-0123456789ab")).doesNotThrowAnyException();
        verify(provisioner, times(2)).ensure("u-0123456789ab");
    }
}
