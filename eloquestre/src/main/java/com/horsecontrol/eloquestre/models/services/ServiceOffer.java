package com.horsecontrol.eloquestre.models.services;

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
import java.util.Set;

/** Serviço oferecido por um prestador (veterinário, ferrador...) numa região (raio a partir da cidade). */
@Document(collection = "services")
@CompoundIndexes({
        @CompoundIndex(name = "provider_created_idx", def = "{ 'providerId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "active_occ_idx", def = "{ 'active': 1, 'occupationId': 1 }")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOffer {

    public static final Set<Integer> RADII = Set.of(10, 20, 50, 100, 200);

    @Id
    private String id;
    private String providerId;
    private String occupationId;
    private String title;
    private String description;
    private Double priceFrom;
    /** hora | dia | mes | servico */
    private String priceUnit;
    @Builder.Default
    private int radiusKm = 50;
    @Builder.Default
    private boolean active = true;
    private CityRef city;
    private Instant createdAt;
}
