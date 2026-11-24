package com.horsecontrol.eloquestre.controllers.profile;

import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import com.horsecontrol.eloquestre.dto.ProfileDto;
import com.horsecontrol.eloquestre.services.profile.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Perfis. Todas as rotas exigem JWT (ver JwtAuthFilter). */
@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** GET /me — perfil completo do usuário logado. */
    @GetMapping("/me")
    public ProfileDto me(@RequestAttribute(JwtAuthFilter.USER_ID) String userId) {
        return profileService.getMe(userId);
    }

    /** PATCH /me — atualização parcial (chaves em snake_case, como em Profile do app). */
    @PatchMapping("/me")
    public ProfileDto updateMe(@RequestAttribute(JwtAuthFilter.USER_ID) String userId,
                               @RequestBody Map<String, Object> patch) {
        return profileService.updateMe(userId, patch);
    }

    /** GET /profiles/{id} — só perfis com cadastro concluído. */
    @GetMapping("/profiles/{id}")
    public ProfileDto profile(@PathVariable String id) {
        return profileService.getPublic(id);
    }

    @GetMapping("/profiles/by-username/{username}")
    public ProfileDto byUsername(@PathVariable String username) {
        return profileService.getByUsername(username);
    }

    /** GET /usernames/{u}/available — ignora o próprio usuário. */
    @GetMapping("/usernames/{username}/available")
    public Map<String, Boolean> usernameAvailable(@RequestAttribute(JwtAuthFilter.USER_ID) String userId,
                                                  @PathVariable String username) {
        return Map.of("available", profileService.usernameAvailable(username, userId));
    }

    /** GET /professionals?lat&lng&radius&occupation&servicesOnly&includeRelocate&q&page */
    @GetMapping("/professionals")
    public List<ProfileDto> professionals(
            @RequestAttribute(JwtAuthFilter.USER_ID) String userId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String occupation,
            @RequestParam(defaultValue = "false") boolean servicesOnly,
            @RequestParam(defaultValue = "true") boolean includeRelocate,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        return profileService.searchProfessionals(userId,
                new ProfileService.PeopleSearch(lat, lng, radius, occupation, servicesOnly, includeRelocate, q, page));
    }
}
