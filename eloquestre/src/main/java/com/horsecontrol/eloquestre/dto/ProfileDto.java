package com.horsecontrol.eloquestre.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.horsecontrol.eloquestre.enums.account.AccountType;
import com.horsecontrol.eloquestre.enums.account.ContractType;
import com.horsecontrol.eloquestre.models.people.CityRef;
import com.horsecontrol.eloquestre.models.people.User;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Set;

/**
 * Perfil no formato do app (src/lib/types.ts → Profile / ProfileCard), em snake_case
 * (spring.jackson.property-naming-strategy=SNAKE_CASE).
 * onboarded/plan/plan_expires_at só vão no perfil do próprio usuário (GET /me).
 */
public record ProfileDto(
        String id,
        AccountType accountType,
        String firstName,
        String lastName,
        String username,
        String avatarUrl,
        String bio,
        String occupationId,
        List<String> otherOccupations,
        Integer experienceYears,
        Integer cityIbge,
        String cityName,
        String state,
        Double lat,
        Double lng,
        int workRadiusKm,
        boolean willingToRelocate,
        Set<ContractType> contractTypes,
        boolean available,
        boolean offersServices,
        Integer serviceRadiusKm,
        String nationality,
        List<String> languages,
        double ratingAvg,
        int ratingCount,
        boolean verified,
        String createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean onboarded,
        @JsonInclude(JsonInclude.Include.NON_NULL) String plan,
        @JsonInclude(JsonInclude.Include.NON_NULL) String planExpiresAt) {

    /** Perfil completo do próprio usuário. */
    public static ProfileDto mine(User u) {
        return of(u, true);
    }

    /** Cartão público (sem onboarded/plano). */
    public static ProfileDto card(User u) {
        return of(u, false);
    }

    private static ProfileDto of(User u, boolean mine) {
        CityRef c = u.getCity();
        boolean hasGeo = c != null && c.getLocation() != null;
        String created = u.getCreatedAt() != null
                ? u.getCreatedAt().toString()
                : (ObjectId.isValid(u.getId()) ? new ObjectId(u.getId()).getDate().toInstant().toString() : null);
        return new ProfileDto(
                u.getId(),
                u.getAccountType(),
                u.getFirstName() != null ? u.getFirstName() : "",
                u.getLastName() != null ? u.getLastName() : "",
                u.getUsername(),
                u.getAvatarUrl(),
                u.getBio(),
                u.getOccupationId(),
                u.getOtherOccupations() != null ? u.getOtherOccupations() : List.of(),
                u.getExperienceYears(),
                c != null ? c.getCityIbge() : null,
                c != null ? c.getCityName() : null,
                c != null ? c.getState() : null,
                hasGeo ? c.lat() : null,
                hasGeo ? c.lng() : null,
                u.getWorkRadiusKm() != null ? u.getWorkRadiusKm().km() : 50,
                u.isWillingToRelocate(),
                u.getContractTypes() != null ? u.getContractTypes() : Set.of(),
                u.isAvailable(),
                u.isOffersServices(),
                u.getServiceRadiusKm() != null ? u.getServiceRadiusKm().km() : null,
                u.getNationality(),
                u.getLanguages() != null ? u.getLanguages() : List.of(),
                u.getRatingAvg(),
                u.getRatingCount(),
                u.isVerified(),
                created,
                mine ? u.isOnboarded() : null,
                mine ? u.getPlan().json() : null,
                mine && u.getPlanExpiresAt() != null ? u.getPlanExpiresAt().toString() : null);
    }
}
