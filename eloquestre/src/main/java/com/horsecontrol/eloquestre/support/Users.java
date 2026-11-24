package com.horsecontrol.eloquestre.support;

import com.horsecontrol.eloquestre.models.people.User;
import com.horsecontrol.eloquestre.repositories.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Busca de usuários em lote, para montar os "cards" (nome, foto, nota) de vagas, posts etc. */
@Component
public class Users {

    private final UserRepository repo;

    public Users(UserRepository repo) {
        this.repo = repo;
    }

    public Map<String, User> byIds(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return repo.findAllById(new HashSet<>(ids)).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public User get(String id) {
        return id == null ? null : repo.findById(id).orElse(null);
    }

    public static String nz(String s) {
        return s != null ? s : "";
    }
}
