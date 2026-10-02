package de.schenk.careertracker.service;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import de.schenk.careertracker.repository.JobApplicationRepository;
import de.schenk.careertracker.web.dto.JobApplicationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository repository;

    public JobApplicationService(JobApplicationRepository repository) {
        this.repository = repository;
    }

    public JobApplication create(JobApplicationRequest request) {
        JobStatus status = request.status() != null ? request.status() : JobStatus.APPLIED;
        LocalDate appliedAt = request.appliedAt() != null ? request.appliedAt() : LocalDate.now();
        JobApplication entity = new JobApplication(
                request.company(), request.position(), status, appliedAt, request.notes());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<JobApplication> list(JobStatus status) {
        if (status == null) {
            return repository.findAllByOrderByAppliedAtDescIdDesc();
        }
        return repository.findByStatusOrderByAppliedAtDescIdDesc(status);
    }

    @Transactional(readOnly = true)
    public JobApplication get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    public JobApplication update(Long id, JobApplicationRequest request) {
        JobApplication entity = get(id);
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

    public void delete(Long id) {
        JobApplication entity = get(id);
        repository.delete(entity);
    }

    /**
     * Number of applications per status; statuses without entries are reported as 0.
     */
    @Transactional(readOnly = true)
    public Map<JobStatus, Long> stats() {
        Map<JobStatus, Long> result = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            result.put(status, 0L);
        }
        for (JobApplication application : repository.findAll()) {
            result.merge(application.getStatus(), 1L, Long::sum);
        }
        return result;
    }
}
