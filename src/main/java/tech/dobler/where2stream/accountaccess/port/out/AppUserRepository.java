package tech.dobler.where2stream.accountaccess.port.out;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.accountaccess.domain.AppUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends ListCrudRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
}
