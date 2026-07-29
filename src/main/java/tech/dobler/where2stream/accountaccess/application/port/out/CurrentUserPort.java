package tech.dobler.where2stream.accountaccess.application.port.out;

import java.util.UUID;

/**
 * Resolves an authenticated username to its user id. This is the one capability other bounded
 * contexts need from Account & Access, published as an interface so they depend on this contract
 * rather than {@code CurrentUserService}'s internals.
 */
public interface CurrentUserPort {

    UUID resolveId(String username);
}
