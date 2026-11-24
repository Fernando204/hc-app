package com.horsecontrol.eloquestre.models.jobs;

import com.horsecontrol.eloquestre.models.people.CityRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Vaga publicada por um contratante. Localização = centro da cidade. */
@Document(collection = "jobs")
@CompoundIndexes({
        @CompoundIndex(name = "status_created_idx", def = "{ 'status': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "employer_created_idx", def = "{ 'employerId': 1, 'createdAt': -1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    public static final Set<String> CONTRACTS = Set.of("clt", "temporario", "diaria", "pj", "prestacao_servico", "estagio");
    public static final Set<String> PERIODS = Set.of("hora", "dia", "mes", "servico");
    public static final Set<String> STATUSES = Set.of("open", "paused", "closed");

    @Id
    private String id;
    private String employerId;
    private String establishmentId;
    private String occupationId;
    private String title;
    private String description;
    @Builder.Default
    private List<String> duties = new ArrayList<>();
    private String requirements;
    private String contractType;
    private Double salaryMin;
    private Double salaryMax;
    private String salaryPeriod;
    private String schedule;
    private Integer hoursPerWeek;
    private boolean housing;
    private boolean meals;
    /** AAAA-MM-DD */
    private String startDate;
    @Builder.Default
    private int vacancies = 1;
    @Builder.Default
    private String status = "open";
    private CityRef city;
    private Instant createdAt;
    private Instant updatedAt;
}
