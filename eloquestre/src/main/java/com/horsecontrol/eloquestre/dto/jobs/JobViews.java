package com.horsecontrol.eloquestre.dto.jobs;

import com.horsecontrol.eloquestre.models.jobs.Establishment;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.jobs.JobApplication;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.support.Users;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON de estabelecimentos, vagas e candidaturas no formato exato do app
 * (src/lib/types.ts: Establishment, Job, JobCard, ApplicationCard).
 */
public final class JobViews {

    private JobViews() {
    }

    public static void putCity(Map<String, Object> m, CityRef c) {
        boolean geo = c != null && c.getLocation() != null;
        m.put("city_ibge", c != null ? c.getCityIbge() : null);
        m.put("city_name", c != null ? c.getCityName() : "");
        m.put("state", c != null ? c.getState() : "");
        m.put("lat", geo ? c.lat() : null);
        m.put("lng", geo ? c.lng() : null);
    }

    public static String iso(Instant i) {
        return i != null ? i.toString() : null;
    }

    public static Map<String, Object> establishment(Establishment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("owner_id", e.getOwnerId());
        m.put("name", e.getName());
        m.put("type", e.getType());
        m.put("description", e.getDescription());
        putCity(m, e.getCity());
        return m;
    }

    public static Map<String, Object> job(Job j, long applications) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("employer_id", j.getEmployerId());
        m.put("establishment_id", j.getEstablishmentId());
        m.put("occupation_id", j.getOccupationId());
        m.put("title", j.getTitle());
        m.put("description", j.getDescription());
        m.put("duties", j.getDuties());
        m.put("requirements", j.getRequirements());
        m.put("contract_type", j.getContractType());
        m.put("salary_min", j.getSalaryMin());
        m.put("salary_max", j.getSalaryMax());
        m.put("salary_period", j.getSalaryPeriod());
        m.put("schedule", j.getSchedule());
        m.put("hours_per_week", j.getHoursPerWeek());
        m.put("housing", j.isHousing());
        m.put("meals", j.isMeals());
        m.put("start_date", j.getStartDate());
        m.put("vacancies", j.getVacancies());
        m.put("status", j.getStatus());
        m.put("applications_count", applications);
        m.put("created_at", iso(j.getCreatedAt()));
        m.put("updated_at", iso(j.getUpdatedAt()));
        putCity(m, j.getCity());
        return m;
    }

    public static Map<String, Object> card(Job j, long applications, Establishment est, User employer, boolean appliedByMe) {
        Map<String, Object> m = job(j, applications);
        m.put("establishment_name", est != null ? est.getName() : null);
        m.put("establishment_type", est != null ? est.getType() : null);
        m.put("employer_first_name", Users.nz(employer.getFirstName()));
        m.put("employer_last_name", Users.nz(employer.getLastName()));
        m.put("employer_username", employer.getUsername());
        m.put("employer_avatar_url", employer.getAvatarUrl());
        m.put("employer_verified", employer.isVerified());
        m.put("employer_rating_avg", employer.getRatingAvg());
        m.put("employer_rating_count", employer.getRatingCount());
        m.put("applied_by_me", appliedByMe);
        return m;
    }

    public static Map<String, Object> application(JobApplication a, Job j, User applicant) {
        Map<String, Object> m = new LinkedHashMap<>();
        CityRef c = applicant.getCity();
        m.put("id", a.getId());
        m.put("job_id", a.getJobId());
        m.put("applicant_id", a.getApplicantId());
        m.put("message", a.getMessage());
        m.put("status", a.getStatus());
        m.put("created_at", iso(a.getCreatedAt()));
        m.put("job_title", j.getTitle());
        m.put("employer_id", j.getEmployerId());
        m.put("applicant_first_name", Users.nz(applicant.getFirstName()));
        m.put("applicant_last_name", Users.nz(applicant.getLastName()));
        m.put("applicant_username", applicant.getUsername());
        m.put("applicant_avatar_url", applicant.getAvatarUrl());
        m.put("applicant_occupation_id", applicant.getOccupationId());
        m.put("applicant_city_name", c != null ? c.getCityName() : null);
        m.put("applicant_state", c != null ? c.getState() : null);
        m.put("applicant_rating_avg", applicant.getRatingAvg());
        m.put("applicant_rating_count", applicant.getRatingCount());
        return m;
    }
}
