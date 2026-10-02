package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import de.schenk.careertracker.repository.JobApplicationRepository;
import de.schenk.careertracker.web.dto.JobApplicationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository repository;

    private JobApplicationService service() {
        return new JobApplicationService(repository);
    }

    @Test
    void createDefaultsStatusToAppliedAndDateToToday() {
        when(repository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication created = service().create(
                new JobApplicationRequest("Bechtle", "Junior Developer", null, null, null));

        assertThat(created.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(created.getAppliedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void getThrowsWhenApplicationDoesNotExist() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(42L))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void updateRejectsInvalidStatusTransition() {
        JobApplication existing = new JobApplication(
                "ACME", "Developer", JobStatus.REJECTED, LocalDate.now(), null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        JobApplicationRequest request = new JobApplicationRequest(
                "ACME", "Developer", JobStatus.INTERVIEW, null, null);

        assertThatThrownBy(() -> service().update(1L, request))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesValidTransition() {
        JobApplication existing = new JobApplication(
                "ACME", "Developer", JobStatus.APPLIED, LocalDate.now(), null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication updated = service().update(1L, new JobApplicationRequest(
                "ACME", "Senior Developer", JobStatus.INTERVIEW, null, "Phone screen booked"));

        assertThat(updated.getStatus()).isEqualTo(JobStatus.INTERVIEW);
        assertThat(updated.getPosition()).isEqualTo("Senior Developer");
        assertThat(updated.getNotes()).isEqualTo("Phone screen booked");
    }

    @Test
    void statsContainsAllStatusesWithZeroDefaults() {
        when(repository.findAll()).thenReturn(java.util.List.of(
                new JobApplication("A", "X", JobStatus.APPLIED, LocalDate.now(), null),
                new JobApplication("B", "Y", JobStatus.APPLIED, LocalDate.now(), null),
                new JobApplication("C", "Z", JobStatus.OFFER, LocalDate.now(), null)));

        Map<JobStatus, Long> stats = service().stats();

        assertThat(stats).hasSize(JobStatus.values().length);
        assertThat(stats.get(JobStatus.APPLIED)).isEqualTo(2L);
        assertThat(stats.get(JobStatus.OFFER)).isEqualTo(1L);
        assertThat(stats.get(JobStatus.REJECTED)).isZero();
    }
}
