package com.horsecontrol.eloquestre.repositories;

import com.horsecontrol.eloquestre.models.people.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    /** Disponibilidade de username ignorando o próprio usuário. */
    boolean existsByUsernameAndIdNot(String username, String id);
}
