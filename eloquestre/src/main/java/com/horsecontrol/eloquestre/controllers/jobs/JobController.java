package com.horsecontrol.eloquestre.controllers.jobs;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.services.jobs.JobService;
import com.horsecontrol.eloquestre.services.settings.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Estabelecimentos, vagas e candidaturas. */
@RestController
public class JobController {

    private final JobService jobs;
    private final PlanService plans;

    public JobController(JobService jobs, PlanService plans) {
        this.jobs = jobs;
        this.plans = plans;
    }

    // ------------------------------------------------------------ estabelecimentos (só os meus)
    @GetMapping("/establishments")
    public List<Map<String, Object>> myEstablishments(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return jobs.myEstablishments(me);
    }

    @PostMapping("/establishments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createEstablishment(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, Object> body) {
        return jobs.saveEstablishment(me, null, body);
    }

    @PutMapping("/establishments/{id}")
    public Map<String, Object> updateEstablishment(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return jobs.saveEstablishment(me, id, body);
    }

    @DeleteMapping("/establishments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEstablishment(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        jobs.deleteEstablishment(me, id);
    }

    // ------------------------------------------------------------ vagas
    /** GET /jobs?lat&lng&radius&occupation&contract&q&page */
    @GetMapping("/jobs")
    public List<Map<String, Object>> search(@RequestAttribute(JwtAuthFilter.USER_ID) String me,
                                            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                            @RequestParam(required = false) Integer radius, @RequestParam(required = false) String occupation,
                                            @RequestParam(required = false) String contract, @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page) {
        return jobs.search(me, new JobService.JobSearch(lat, lng, radius, occupation, contract, q, page));
    }

    @GetMapping("/jobs/{id}")
    public Map<String, Object> get(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return jobs.get(me, id);
    }

    @GetMapping("/me/jobs")
    public List<Map<String, Object>> mine(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return jobs.mine(me);
    }

    @GetMapping("/me/can-post-jobs")
    public Map<String, Boolean> canPostJobs(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return Map.of("can_post_jobs", plans.canPostJobs(me));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, Object> body) {
        return jobs.save(me, null, body);
    }

    @PutMapping("/jobs/{id}")
    public Map<String, Object> update(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return jobs.save(me, id, body);
    }

    /** PATCH /jobs/{id} {status: open|paused|closed} */
    @PatchMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStatus(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, String> body) {
        jobs.setStatus(me, id, body.get("status"));
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        jobs.delete(me, id);
    }

    // ------------------------------------------------------------ candidaturas
    /** POST /jobs/{id}/applications {message} → {conversation_id} */
    @PostMapping("/jobs/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> apply(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return Map.of("conversation_id", jobs.apply(me, id, body != null ? body.get("message") : null));
    }

    @GetMapping("/me/applications")
    public List<Map<String, Object>> myApplications(@RequestAttribute(JwtAuthFilter.USER_ID) String me) {
        return jobs.myApplications(me);
    }

    @GetMapping("/jobs/{id}/applications")
    public List<Map<String, Object>> applicationsOf(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id) {
        return jobs.applicationsOf(me, id);
    }

    @PatchMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setApplicationStatus(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @PathVariable String id, @RequestBody Map<String, String> body) {
        jobs.setApplicationStatus(me, id, body.get("status"));
    }
}
