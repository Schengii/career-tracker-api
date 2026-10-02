package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import de.schenk.careertracker.repository.JobApplicationRepository;
import de.schenk.careertracker.web.dto.JobApplicationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * All operations are scoped to an owner (the authenticated user). Accessing another
 * user's application behaves exactly like accessing a non-existent one (404).
 */
@Service
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository repository;

    public JobApplicationService(JobApplicationRepository repository) {
        this.repository = repository;
    }

    public JobApplication create(String owner, JobApplicationRequest request) {
        JobStatus status = request.status() != null ? request.status() : JobStatus.APPLIED;
        LocalDate appliedAt = request.appliedAt() != null ? request.appliedAt() : LocalDate.now();
        JobApplication entity = new JobApplication(
                owner, request.company(), request.position(), status, appliedAt, request.notes());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<JobApplication> list(String owner, JobStatus status, Pageable pageable) {
        if (status == null) {
            return repository.findByOwner(owner, pageable);
        }
        return repository.findByOwnerAndStatus(owner, status, pageable);
    }

    @Transactional(readOnly = true)
    public JobApplication get(Long id, String owner) {
        return repository.findByIdAndOwner(id, owner).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    public JobApplication update(Long id, String owner, JobApplicationRequest request) {
        JobApplication entity = get(id, owner);
        if (request.status() != null && !entity.getStatus().canTransitionTo(request.status())) {
            throw new InvalidStatusTransitionException(entity.getStatus(), request.status());
        }
        entity.setCompany(request.company());
        entity.setPosition(request.position());
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.appliedAt() != null) {
            entity.setAppliedAt(request.appliedAt());
        }
        entity.setNotes(request.notes());
        return repository.save(entity);
    }

    public void delete(Long id, String owner) {
        repository.delete(get(id, owner));
    }

    /**
     * Number of the owner's applications per status; statuses without entries are reported as 0.
     */
    @Transactional(readOnly = true)
    public Map<JobStatus, Long> stats(String owner) {
        Map<JobStatus, Long> result = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            result.put(status, 0L);
        }
        for (Object[] row : repository.countByStatusForOwner(owner)) {
            result.put((JobStatus) row[0], (Long) row[1]);
        }
        return result;
    }
}
