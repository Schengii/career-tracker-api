package de.schenk.careertracker.repository;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByStatusOrderByAppliedAtDescIdDesc(JobStatus status);

    List<JobApplication> findAllByOrderByAppliedAtDescIdDesc();
}
