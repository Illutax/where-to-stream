package tech.dobler.where2stream.accountaccess.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.accountaccess.port.in.UserMetricsPort;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;

/** Account &amp; Access's side of the instance metrics: how many accounts exist. */
@Service
@RequiredArgsConstructor
public class UserMetricsService implements UserMetricsPort {

    private final AppUserRepository repository;

    @Override
    public long countUsers() {
        return repository.count();
    }
}
