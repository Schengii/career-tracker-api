package de.schenk.careertracker.web;

import de.schenk.careertracker.domain.JobApplication;
import de.schenk.careertracker.domain.JobStatus;
import de.schenk.careertracker.service.JobApplicationService;
import de.schenk.careertracker.web.dto.JobApplicationRequest;
import de.schenk.careertracker.web.dto.JobApplicationResponse;
import de.schenk.careertracker.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/applications")
@SecurityRequirement(name = "bearerAuth")
public class JobApplicationController {

    private static final Set<String> SORTABLE = Set.of("id", "company", "position", "status", "appliedAt");

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody JobApplicationRequest request) {
        JobApplication created = service.create(jwt.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(JobApplicationResponse.from(created));
    }

    /**
     * Paged list. Query params: {@code page} (0-based), {@code size} (max 100),
     * {@code sort} (e.g. {@code company,asc}) and the optional {@code status} filter.
     */
    @GetMapping
    public PageResponse<JobApplicationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = {"appliedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), SORTABLE);
            }
        });
        return PageResponse.from(service.list(jwt.getSubject(), status, pageable), JobApplicationResponse::from);
    }

    @GetMapping("/stats")
    public Map<JobStatus, Long> stats(@AuthenticationPrincipal Jwt jwt) {
        return service.stats(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public JobApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return JobApplicationResponse.from(service.get(id, jwt.getSubject()));
    }

    @PutMapping("/{id}")
    public JobApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                         @Valid @RequestBody JobApplicationRequest request) {
        return JobApplicationResponse.from(service.update(id, jwt.getSubject(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
