package com.pixous.hrportal.security;

import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads a user by username for the authentication manager. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user for username " + username));
        return new UserPrincipal(user);
    }

    /**
     * The busiest read in the application: it runs once per authenticated request.
     *
     * <p>The query fetches the roles and their permissions along with the user, in
     * one round trip. It used to be findById, which looks cheaper and is not —
     * building {@link UserPrincipal} touches both collections, so the round trips
     * happened anyway, one at a time, after this method had returned.
     */
    @Transactional(readOnly = true)
    public UserDetails loadById(Long id) {
        User user = userRepository.findByIdWithAuthorities(id)
                .orElseThrow(() -> new UsernameNotFoundException("No user id " + id));
        return new UserPrincipal(user);
    }
}
