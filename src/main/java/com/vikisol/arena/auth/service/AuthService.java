package com.vikisol.arena.auth.service;

import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.seed.SeedDataFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final SeedDataFactory seedDataFactory;

    @Transactional
    public SessionResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Role role = Role.fromWireValue(request.role());
        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(role)
                .build();
        user = userRepository.save(user);

        String candidateId = null;
        if (role == Role.TALENT) {
            CandidateProfile profile = seedDataFactory.blankCandidateProfile(user);
            profile = candidateProfileRepository.save(profile);
            candidateId = profile.getId().toString();
        } else {
            EnterpriseProfile profile = seedDataFactory.blankEnterpriseProfile(user);
            enterpriseProfileRepository.save(profile);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        return new SessionResponse(role.wireValue(), candidateId, user.getName(), user.getEmail(), token);
    }

    @Transactional(readOnly = true)
    public SessionResponse signIn(SignInRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String candidateId = null;
        if (user.getRole() == Role.TALENT) {
            candidateId = candidateProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getId().toString())
                    .orElse(null);
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        return new SessionResponse(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), token);
    }

    @Transactional(readOnly = true)
    public SessionResponse currentSession(User user) {
        String candidateId = null;
        if (user.getRole() == Role.TALENT) {
            candidateId = candidateProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getId().toString())
                    .orElse(null);
        }
        return new SessionResponse(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), null);
    }
}
