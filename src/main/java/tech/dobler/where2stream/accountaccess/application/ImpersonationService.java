package tech.dobler.where2stream.accountaccess.application;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.accountaccess.adapter.in.security.ImpersonationConfig;
import tech.dobler.where2stream.accountaccess.port.in.ImpersonationPort;

import java.util.Optional;

/** Serves {@link ImpersonationPort} from the authority {@code SwitchUserFilter} leaves behind. */
@Service
public class ImpersonationService implements ImpersonationPort {

    @Override
    public Optional<String> impersonatingAdmin(Authentication authentication) {
        return ImpersonationConfig.originalUsername(authentication);
    }
}
