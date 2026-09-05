package tech.dobler.where2stream.accountaccess.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.accountaccess.port.in.UserDirectoryPort;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;

/** Serves {@link UserDirectoryPort} from the user table. */
@Service
public class UserDirectoryService implements UserDirectoryPort {

    private final AppUserRepository users;

    public UserDirectoryService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public long registeredUserCount() {
        return users.count();
    }
}
