package com.horsecontrol.eloquestre.models.jobs;

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
import java.util.Set;

/** Candidatura (interesse) de um profissional numa vaga. Única por (vaga, candidato). */
@Document(collection = "applications")
@CompoundIndexes({
        @CompoundIndex(name = "job_applicant_idx", def = "{ 'jobId': 1, 'applicantId': 1 }", unique = true),
        @CompoundIndex(name = "applicant_created_idx", def = "{ 'applicantId': 1, 'createdAt': -1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    public static final Set<String> STATUSES = Set.of("sent", "viewed", "shortlisted", "hired", "declined");

    @Id
    private String id;
    private String jobId;
    private String applicantId;
    /** Dono da vaga (desnormalizado para checar permissão e excluir conta). */
    private String employerId;
    private String message;
    private String status;
    private Instant createdAt;
}
