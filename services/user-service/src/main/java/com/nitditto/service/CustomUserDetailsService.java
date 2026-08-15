package com.nitditto.service;

import com.nitditto.model.User;
import com.nitditto.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email);

    if (user == null) {
      throw new UsernameNotFoundException(email);
    }
    GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().toString());
    Collection<GrantedAuthority> grantedAuthorities = Collections.singletonList(authority);

    return new org.springframework.security.core.userdetails.User(
        user.getEmail(), user.getPassword(), grantedAuthorities
    );
  }
}
