package com.horsecontrol.eloquestre.models.jobs;

import com.horsecontrol.eloquestre.models.people.CityRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

/** Haras, hípica, centro de treinamento... de um contratante. */
@Document(collection = "establishments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Establishment {

    public static final Set<String> TYPES = Set.of("haras", "hipica", "centro_treinamento", "centro_reproducao", "fazenda", "outro");

    @Id
    private String id;
    @Indexed
    private String ownerId;
    private String name;
    private String type;
    private String description;
    private CityRef city;
    private Instant createdAt;
}
