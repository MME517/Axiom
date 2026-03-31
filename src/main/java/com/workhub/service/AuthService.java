package com.workhub.service;

import com.workhub.dto.request.LoginRequest;
import com.workhub.dto.response.LoginResponse;
import com.workhub.dto.response.UserResponse;
import com.workhub.entity.Tenant;
import com.workhub.entity.User;
import com.workhub.exception.ResourceNotFoundException;
import com.workhub.repository.TenantRepository;
import com.workhub.repository.UserRepository;
import com.workhub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .tenantId(user.getTenantId())
                .roles(user.getRoles())
                .build();
    }

    public UserResponse getMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found"));

        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .tenantId(user.getTenantId())
                .tenantName(tenant.getName())
                .tenantPlan(tenant.getPlan())
                .roles(user.getRoles())
                .build();
    }
}
