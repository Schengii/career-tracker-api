package de.schenk.careertracker.repository;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Page<JobApplication> findByOwner(String owner, Pageable pageable);

    Page<JobApplication> findByOwnerAndStatus(String owner, JobStatus status, Pageable pageable);

    Optional<JobApplication> findByIdAndOwner(Long id, String owner);

    @Query("select a.status, count(a) from JobApplication a where a.owner = :owner group by a.status")
    List<Object[]> countByStatusForOwner(@Param("owner") String owner);
}
