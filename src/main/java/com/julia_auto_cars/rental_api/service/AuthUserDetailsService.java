package com.julia_auto_cars.rental_api.service;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.julia_auto_cars.rental_api.repository.UserAccountRepository;

@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final UserAccountRepository repository;

    public AuthUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = repository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream().map(Enum::name).toArray(String[]::new))
                .build();
    }
}
