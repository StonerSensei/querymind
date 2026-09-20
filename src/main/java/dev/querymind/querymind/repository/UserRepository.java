package dev.querymind.querymind.repository;

import dev.querymind.querymind.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Database access for users.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    // Used at login time to find a user by their email.
    Optional<User> findByEmail(String email);
}
