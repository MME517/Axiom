package com.workhub.config;

import com.workhub.entity.Tenant;
import com.workhub.entity.User;
import com.workhub.repository.TenantRepository;
import com.workhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        if (tenantRepository.count() > 0) return; // don't re-seed


        Tenant tenantA = tenantRepository.save(
                Tenant.builder()
                        .name("Acme Corp")
                        .plan("PRO")
                        .build());


        Tenant tenantB = tenantRepository.save(
                Tenant.builder()
                        .name("Beta Inc")
                        .plan("FREE")
                        .build());


        userRepository.save(User.builder()
                .email("admin@acme.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .tenantId(tenantA.getTenantId())
                .roles(Set.of("TENANT_ADMIN"))
                .build());


        userRepository.save(User.builder()
                .email("user@acme.com")
                .passwordHash(passwordEncoder.encode("user123"))
                .tenantId(tenantA.getTenantId())
                .roles(Set.of("TENANT_USER"))
                .build());


        userRepository.save(User.builder()
                .email("admin@beta.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .tenantId(tenantB.getTenantId())
                .roles(Set.of("TENANT_ADMIN"))
                .build());

        System.out.println("Test data seeded successfully");
    }
}