package com.horsecontrol.eloquestre.services.jobs;

import com.horsecontrol.eloquestre.dto.jobs.JobViews;
import com.horsecontrol.eloquestre.models.jobs.Establishment;
import com.horsecontrol.eloquestre.models.jobs.Job;
import com.horsecontrol.eloquestre.models.jobs.JobApplication;
import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.services.chat.ChatService;
import com.horsecontrol.eloquestre.services.moderation.BlockService;
import com.horsecontrol.eloquestre.services.push.PushService;
import com.horsecontrol.eloquestre.services.settings.PlanService;
import com.horsecontrol.eloquestre.support.Body;
import com.horsecontrol.eloquestre.support.Geo;
import com.horsecontrol.eloquestre.support.Users;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Estabelecimentos, vagas e candidaturas (regras de docs/MIGRACAO-SPRING-BOOT.md). */
@Service
public class JobService {

    public static final int PAGE_SIZE = 20;
    private static final int MAX_CANDIDATES = 1000;

    private final MongoTemplate mongo;
    private final Users users;
    private final BlockService blocks;
    private final PlanService plans;
    private final ChatService chat;
    private final PushService push;

    public JobService(MongoTemplate mongo, Users users, BlockService blocks, PlanService plans, ChatService chat, PushService push) {
        this.push = push;
        this.mongo = mongo;
        this.users = users;
        this.blocks = blocks;
        this.plans = plans;
        this.chat = chat;
    }

    // ================================================================ estabelecimentos
    public List<Map<String, Object>> myEstablishments(String me) {
        return mongo.find(Query.query(Criteria.where("ownerId").is(me)).with(Sort.by("createdAt")), Establishment.class)
                .stream().map(JobViews::establishment).toList();
    }

    public Map<String, Object> saveEstablishment(String me, String id, Map<String, Object> raw) {
        Body b = Body.of(raw);
        Establishment e = id == null ? Establishment.builder().ownerId(me).createdAt(Instant.now()).build() : ownEstablishment(me, id);
        if (id == null || b.has("name")) e.setName(b.text("name", 120));
        if (id == null || b.has("type")) e.setType(b.oneOf("type", Establishment.TYPES, true));
        if (b.has("description")) e.setDescription(b.optText("description", 2000));
        e.setCity(b.city(e.getCity(), true));
        return JobViews.establishment(mongo.save(e));
    }

    /** Ao excluir, as vagas ficam sem estabelecimento (establishment_id = null). */
    public void deleteEstablishment(String me, String id) {
        Establishment e = ownEstablishment(me, id);
        mongo.updateMulti(Query.query(Criteria.where("establishmentId").is(e.getId())), new Update().set("establishmentId", null), Job.class);
        mongo.remove(e);
    }

    private Establishment ownEstablishment(String me, String id) {
        Establishment e = mongo.findById(id, Establishment.class);
        if (e == null || !e.getOwnerId().equals(me)) throw Body.notFound("Estabelecimento não encontrado.");
        return e;
    }

    // ================================================================ vagas
    public record JobSearch(Double lat, Double lng, Integer radius, String occupation, String contract, String query, int page) {
    }

    /** Só vagas abertas. Com lat/lng: dentro do raio, por distância e depois as mais novas. 20 por página. */
    public List<Map<String, Object>> search(String me, JobSearch p) {
        boolean geo = p.lat() != null && p.lng() != null;
        double radius = p.radius() != null && p.radius() > 0 ? Math.min(p.radius(), 500) : 50;
        Set<String> hidden = blocks.hiddenFor(me);

        Criteria c = Criteria.where("status").is("open");
        if (notBlank(p.occupation())) c = c.and("occupationId").is(p.occupation());
        if (notBlank(p.contract())) c = c.and("contractType").is(p.contract());
        if (!hidden.isEmpty()) c = c.and("employerId").nin(hidden);
        Query q = Query.query(geo ? new Criteria().andOperator(c, Geo.within("city.location", p.lat(), p.lng(), radius)) : c)
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(MAX_CANDIDATES);
        List<Job> jobs = mongo.find(q, Job.class);

        Map<String, Establishment> ests = establishmentsOf(jobs);
        if (notBlank(p.query())) {
            jobs = jobs.stream().filter(j -> Geo.matches(p.query(), j.getTitle(), j.getDescription(),
                    j.getEstablishmentId() != null && ests.containsKey(j.getEstablishmentId()) ? ests.get(j.getEstablishmentId()).getName() : null)).toList();
        }
        if (geo) {
            jobs = new ArrayList<>(jobs);
            jobs.sort(Comparator.<Job>comparingDouble(j -> Geo.km(j.getCity(), p.lat(), p.lng()))
                    .thenComparing(Job::getCreatedAt, Comparator.reverseOrder()));
        }
        List<Job> page = jobs.stream().skip((long) Math.max(0, p.page()) * PAGE_SIZE).limit(PAGE_SIZE).toList();
        return cards(page, me, ests);
    }

    public Map<String, Object> get(String me, String id) {
        Job j = mongo.findById(id, Job.class);
        if (j == null) throw Body.notFound("Vaga não encontrada.");
        if (!j.getEmployerId().equals(me) && blocks.blockedBetween(me, j.getEmployerId())) throw Body.notFound("Vaga não encontrada.");
        List<Map<String, Object>> out = cards(List.of(j), me, establishmentsOf(List.of(j)));
        if (out.isEmpty()) throw Body.notFound("Vaga não encontrada.");
        return out.get(0);
    }

    public List<Map<String, Object>> mine(String me) {
        List<Job> jobs = mongo.find(Query.query(Criteria.where("employerId").is(me)).with(Sort.by(Sort.Direction.DESC, "createdAt")), Job.class);
        return cards(jobs, me, establishmentsOf(jobs));
    }

    /** Cria (exige poder publicar) ou edita (só o dono). */
    public Map<String, Object> save(String me, String id, Map<String, Object> raw) {
        Body b = Body.of(raw);
        Job j;
        if (id == null) {
            plans.ensureCanPostJobs(me);
            User u = users.get(me);
            if (u == null || !u.isEmployer()) throw Body.forbidden("Só contas de contratante publicam vagas.");
            j = Job.builder().employerId(me).createdAt(Instant.now()).build();
        } else {
            j = ownJob(me, id);
        }
        boolean create = id == null;
        if (create || b.has("title")) j.setTitle(b.text("title", 120));
        if (create || b.has("description")) j.setDescription(nz(b.optText("description", 5000))); // opcional no app
        if (create || b.has("occupation_id")) j.setOccupationId(b.text("occupation_id", 60));
        if (create || b.has("contract_type")) j.setContractType(b.oneOf("contract_type", Job.CONTRACTS, true));
        if (b.has("establishment_id")) {
            String est = b.optText("establishment_id", 40);
            if (est != null) ownEstablishment(me, est);
            j.setEstablishmentId(est);
        }
        if (b.has("duties")) j.setDuties(b.strings("duties", 30, 200));
        if (b.has("requirements")) j.setRequirements(b.optText("requirements", 2000));
        if (b.has("salary_min")) j.setSalaryMin(b.optMoney("salary_min"));
        if (b.has("salary_max")) j.setSalaryMax(b.optMoney("salary_max"));
        if (b.has("salary_period")) j.setSalaryPeriod(b.oneOf("salary_period", Job.PERIODS, false));
        if (b.has("schedule")) j.setSchedule(b.optText("schedule", 200));
        if (b.has("hours_per_week")) j.setHoursPerWeek(b.optInt("hours_per_week", 1, 80));
        if (b.has("housing")) j.setHousing(b.bool("housing", false));
        if (b.has("meals")) j.setMeals(b.bool("meals", false));
        if (b.has("start_date")) j.setStartDate(b.optDate("start_date"));
        if (b.has("vacancies")) j.setVacancies(b.optInt("vacancies", 1, 99) != null ? b.optInt("vacancies", 1, 99) : 1);
        if (b.has("status")) j.setStatus(b.oneOf("status", Job.STATUSES, true));
        j.setCity(b.city(j.getCity(), true));
        if (j.getSalaryMin() != null && j.getSalaryMax() != null && j.getSalaryMin() > j.getSalaryMax()) {
            throw Body.bad("O salário mínimo não pode ser maior que o máximo.");
        }
        j.setUpdatedAt(Instant.now());
        Job saved = mongo.save(j);
        return JobViews.job(saved, countApplications(List.of(saved.getId())).getOrDefault(saved.getId(), 0L));
    }

    public void setStatus(String me, String id, String status) {
        if (status == null || !Job.STATUSES.contains(status)) throw Body.bad("status inválido.");
        Job j = ownJob(me, id);
        j.setStatus(status);
        j.setUpdatedAt(Instant.now());
        mongo.save(j);
    }

    /** Exclui a vaga e as candidaturas dela; publicações que a citavam ficam sem vaga. */
    public void delete(String me, String id) {
        Job j = ownJob(me, id);
        mongo.remove(Query.query(Criteria.where("jobId").is(j.getId())), JobApplication.class);
        mongo.updateMulti(Query.query(Criteria.where("jobId").is(j.getId())), new Update().set("jobId", null), com.horsecontrol.eloquestre.models.feed.Post.class);
        mongo.remove(j);
    }

    private Job ownJob(String me, String id) {
        Job j = mongo.findById(id, Job.class);
        if (j == null || !j.getEmployerId().equals(me)) throw Body.notFound("Vaga não encontrada.");
        return j;
    }

    // ================================================================ candidaturas
    /**
     * Candidata-se (idempotente), abre a conversa com o contratante e envia a mensagem de interesse.
     * Erros: job_not_open, own_job, blocked. Devolve o id da conversa.
     */
    public String apply(String me, String jobId, String message) {
        Job j = mongo.findById(jobId, Job.class);
        if (j == null || !"open".equals(j.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "job_not_open");
        if (j.getEmployerId().equals(me)) throw Body.bad("own_job");
        blocks.ensureNotBlocked(me, j.getEmployerId());
        String text = message == null || message.isBlank() ? null : message.strip();
        if (text != null && text.length() > 2000) throw Body.bad("Mensagem muito longa (máximo de 2000 caracteres).");

        boolean isNew = true;
        try {
            mongo.insert(JobApplication.builder().jobId(j.getId()).applicantId(me).employerId(j.getEmployerId())
                    .message(text).status("sent").createdAt(Instant.now()).build());
        } catch (DuplicateKeyException e) {
            isNew = false; // já tinha se candidatado: só leva à conversa
        }
        String conv = chat.open(me, j.getEmployerId());
        if (isNew) {
            String body = "Olá! Tenho interesse na vaga \"" + j.getTitle() + "\"." + (text != null ? "\n\n" + text : "");
            chat.send(me, conv, body, "apply-" + j.getId());
            push.notify(j.getEmployerId(), "Novo candidato", push.nameOf(me) + " tem interesse em \"" + j.getTitle() + "\"", "/job/" + j.getId());
        }
        return conv;
    }

    public List<Map<String, Object>> myApplications(String me) {
        List<JobApplication> apps = mongo.find(Query.query(Criteria.where("applicantId").is(me)).with(Sort.by(Sort.Direction.DESC, "createdAt")), JobApplication.class);
        return applicationCards(apps);
    }

    /** Só o dono da vaga vê os candidatos. */
    public List<Map<String, Object>> applicationsOf(String me, String jobId) {
        ownJob(me, jobId);
        List<JobApplication> apps = mongo.find(Query.query(Criteria.where("jobId").is(jobId)).with(Sort.by(Sort.Direction.DESC, "createdAt")), JobApplication.class);
        Set<String> hidden = blocks.hiddenFor(me);
        return applicationCards(apps.stream().filter(a -> !hidden.contains(a.getApplicantId())).toList());
    }

    public void setApplicationStatus(String me, String id, String status) {
        if (status == null || !JobApplication.STATUSES.contains(status)) throw Body.bad("status inválido.");
        JobApplication a = mongo.findById(id, JobApplication.class);
        if (a == null || !a.getEmployerId().equals(me)) throw Body.notFound("Candidatura não encontrada.");
        a.setStatus(status);
        mongo.save(a);
    }

    // ================================================================ montagem dos cards
    private List<Map<String, Object>> cards(List<Job> jobs, String me, Map<String, Establishment> ests) {
        if (jobs.isEmpty()) return List.of();
        List<String> ids = jobs.stream().map(Job::getId).toList();
        Map<String, User> employers = users.byIds(jobs.stream().map(Job::getEmployerId).toList());
        Map<String, Long> counts = countApplications(ids);
        Set<String> appliedByMe = me == null ? Set.of() : mongo.find(Query.query(Criteria.where("jobId").in(ids).and("applicantId").is(me)), JobApplication.class)
                .stream().map(JobApplication::getJobId).collect(Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Job j : jobs) {
            User emp = employers.get(j.getEmployerId());
            if (emp == null) continue; // contratante excluído
            out.add(JobViews.card(j, counts.getOrDefault(j.getId(), 0L), j.getEstablishmentId() != null ? ests.get(j.getEstablishmentId()) : null, emp, appliedByMe.contains(j.getId())));
        }
        return out;
    }

    private List<Map<String, Object>> applicationCards(List<JobApplication> apps) {
        if (apps.isEmpty()) return List.of();
        Map<String, Job> jobs = mongo.find(Query.query(Criteria.where("id").in(apps.stream().map(JobApplication::getJobId).toList())), Job.class)
                .stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<String, User> applicants = users.byIds(apps.stream().map(JobApplication::getApplicantId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobApplication a : apps) {
            Job j = jobs.get(a.getJobId());
            User u = applicants.get(a.getApplicantId());
            if (j != null && u != null) out.add(JobViews.application(a, j, u));
        }
        return out;
    }

    private Map<String, Establishment> establishmentsOf(Collection<Job> jobs) {
        Set<String> ids = new HashSet<>();
        for (Job j : jobs) if (j.getEstablishmentId() != null) ids.add(j.getEstablishmentId());
        if (ids.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("id").in(ids)), Establishment.class).stream()
                .collect(Collectors.toMap(Establishment::getId, Function.identity()));
    }

    private Map<String, Long> countApplications(Collection<String> jobIds) {
        Map<String, Long> out = new HashMap<>();
        Query q = Query.query(Criteria.where("jobId").in(jobIds));
        q.fields().include("jobId");
        for (JobApplication a : mongo.find(q, JobApplication.class)) out.merge(a.getJobId(), 1L, Long::sum);
        return out;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
