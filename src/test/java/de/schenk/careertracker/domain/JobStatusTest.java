package de.schenk.careertracker.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTest {

    @Test
    void applicationCanProgressToInterview() {
        assertThat(JobStatus.APPLIED.canTransitionTo(JobStatus.INTERVIEW)).isTrue();
    }

    @Test
    void applicationCannotSkipDirectlyToOffer() {
        assertThat(JobStatus.APPLIED.canTransitionTo(JobStatus.OFFER)).isFalse();
    }

    @Test
    void offerCanBeAccepted() {
        assertThat(JobStatus.OFFER.canTransitionTo(JobStatus.ACCEPTED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void terminalStatusesCannotBeLeft(JobStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (JobStatus other : JobStatus.values()) {
            if (other != terminal) {
                assertThat(terminal.canTransitionTo(other)).isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void staying_in_same_status_is_always_allowed(JobStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
    }
}
