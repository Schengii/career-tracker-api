package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import de.schenk.careertracker.repository.JobApplicationRepository;
import de.schenk.careertracker.web.dto.JobApplicationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
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

    private static final String OWNER = "alice";

    @Mock
    private JobApplicationRepository repository;

    private JobApplicationService service() {
        return new JobApplicationService(repository);
    }

    private static JobApplication application(String company, JobStatus status) {
        return new JobApplication(OWNER, company, "Developer", status, LocalDate.now(), null);
    }

    @Test
    void createDefaultsStatusToAppliedAndDateToToday() {
        when(repository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication created = service().create(OWNER,
                new JobApplicationRequest("Bechtle", "Junior Developer", null, null, null));

        assertThat(created.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(created.getAppliedAt()).isEqualTo(LocalDate.now());
        assertThat(created.getOwner()).isEqualTo(OWNER);
    }

    @Test
    void getThrowsWhenApplicationDoesNotExistForOwner() {
        when(repository.findByIdAndOwner(42L, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(42L, OWNER))
                .isInstanceOf(ApplicationNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void listWithoutStatusUsesOwnerQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobApplication> page = new PageImpl<>(List.of(application("A", JobStatus.APPLIED)), pageable, 1);
        when(repository.findByOwner(OWNER, pageable)).thenReturn(page);

        assertThat(service().list(OWNER, null, pageable)).isSameAs(page);
    }

    @Test
    void listWithStatusUsesFilteredOwnerQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobApplication> page = new PageImpl<>(List.of(application("A", JobStatus.OFFER)), pageable, 1);
        when(repository.findByOwnerAndStatus(OWNER, JobStatus.OFFER, pageable)).thenReturn(page);

        assertThat(service().list(OWNER, JobStatus.OFFER, pageable)).isSameAs(page);
    }

    @Test
    void updateRejectsInvalidStatusTransition() {
        JobApplication existing = application("ACME", JobStatus.REJECTED);
        when(repository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));

        JobApplicationRequest request = new JobApplicationRequest(
                "ACME", "Developer", JobStatus.INTERVIEW, null, null);

        assertThatThrownBy(() -> service().update(1L, OWNER, request))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateAppliesValidTransition() {
        JobApplication existing = application("ACME", JobStatus.APPLIED);
        when(repository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));
        when(repository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication updated = service().update(1L, OWNER, new JobApplicationRequest(
                "ACME", "Senior Developer", JobStatus.INTERVIEW, null, "Phone screen booked"));

        assertThat(updated.getStatus()).isEqualTo(JobStatus.INTERVIEW);
        assertThat(updated.getPosition()).isEqualTo("Senior Developer");
        assertThat(updated.getNotes()).isEqualTo("Phone screen booked");
    }

    @Test
    void deleteOfForeignApplicationBehavesLikeNotFound() {
        when(repository.findByIdAndOwner(7L, "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(7L, "mallory"))
                .isInstanceOf(ApplicationNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void statsContainsAllStatusesWithZeroDefaults() {
        when(repository.countByStatusForOwner(OWNER)).thenReturn(List.of(
                new Object[]{JobStatus.APPLIED, 2L},
                new Object[]{JobStatus.OFFER, 1L}));

        Map<JobStatus, Long> stats = service().stats(OWNER);

        assertThat(stats).hasSize(JobStatus.values().length);
        assertThat(stats.get(JobStatus.APPLIED)).isEqualTo(2L);
        assertThat(stats.get(JobStatus.OFFER)).isEqualTo(1L);
        assertThat(stats.get(JobStatus.REJECTED)).isZero();
    }
}
