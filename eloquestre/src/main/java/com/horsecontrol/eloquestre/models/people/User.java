package com.horsecontrol.eloquestre.models.people;

import com.horsecontrol.eloquestre.enums.account.AccountType;
import com.horsecontrol.eloquestre.enums.account.ContractType;
import com.horsecontrol.eloquestre.enums.account.Plan;
import com.horsecontrol.eloquestre.enums.account.Radius;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Usuário do ELOequestre = credenciais + perfil (profissional ou contratante).
 * Equivale a auth.users + public.profiles do modelo anterior, num único documento.
 *
 * Campos "somente servidor" (nunca aceitos do cliente): passwordHash, emailVerified,
 * ratingAvg, ratingCount, verified, plan, planExpiresAt.
 */
@Document(collection = "users")
@CompoundIndex(name = "search_professionals_idx", def = "{ 'accountType': 1, 'onboarded': 1, 'available': 1, 'occupationId': 1 }")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    // ------------------------------------------------------------ credenciais
    @NotBlank
    @Email
    @Indexed(unique = true)
    private String email; // sempre em minúsculas

    /** Hash BCrypt/Argon2. Nunca devolver em DTO. */
    @NotBlank
    private String passwordHash;

    private boolean emailVerified;

    /** Sobe a cada troca de senha / "sair de todos os aparelhos": invalida os tokens antigos. */
    private int tokenVersion;

    // ------------------------------------------------------------ perfil
    @NotNull
    @Builder.Default
    private AccountType accountType = AccountType.PROFESSIONAL;

    @Size(max = 60)
    @Builder.Default
    private String firstName = "";

    @Size(max = 60)
    @Builder.Default
    private String lastName = "";

    /** Único. Índice sparse: usuários sem username (antes do onboarding) não colidem. */
    @Indexed(unique = true, sparse = true)
    @Pattern(regexp = "^[a-z0-9_.]{3,30}$")
    private String username;

    private String avatarUrl;

    @Size(max = 600)
    private String bio;

    /** Ocupação principal (id do catálogo, ex.: "tratador", "veterinario"). */
    @Indexed
    private String occupationId;

    @Builder.Default
    private List<String> otherOccupations = new ArrayList<>();

    @Min(0)
    @Max(80)
    private Integer experienceYears;

    /** Cidade-base (centro do município). */
    @Valid
    private CityRef city;

    /** Raio em que aceita trabalhar a partir da cidade. */
    @NotNull
    @Builder.Default
    private Radius workRadiusKm = Radius.KM_50;

    private boolean willingToRelocate;

    @Builder.Default
    private Set<ContractType> contractTypes = new LinkedHashSet<>();

    /** Aparece nas buscas e no mapa como profissional disponível. */
    @Builder.Default
    private boolean available = true;

    // Prestador de serviço (veterinário, ferrador...) atendendo uma região
    private boolean offersServices;

    private Radius serviceRadiusKm;

    private String nationality;

    @Builder.Default
    private List<String> languages = new ArrayList<>();

    // ------------------------------------------------------------ reputação (somente servidor)
    /** Média das avaliações (0–5, 2 casas). Recalculada a cada avaliação criada/editada/removida. */
    private double ratingAvg;

    private int ratingCount;

    /** Selo de verificação, concedido pelo servidor. */
    private boolean verified;

    /** true quando concluiu o cadastro (nome, username, cidade, ocupação...). */
    private boolean onboarded;

    // ------------------------------------------------------------ assinatura (somente servidor)
    /** Gravado só pelo webhook da RevenueCat. */
    @NotNull
    @Builder.Default
    private Plan plan = Plan.FREE;

    private Instant planExpiresAt;

    // ------------------------------------------------------------ auditoria
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Controle de concorrência otimista. */
    @Version
    private Long version;

    // ------------------------------------------------------------ regras de domínio
    public boolean isEmployer() {
        return accountType == AccountType.EMPLOYER;
    }

    public boolean hasActivePlan() {
        return plan == Plan.STANDARD && (planExpiresAt == null || planExpiresAt.isAfter(Instant.now()));
    }

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}