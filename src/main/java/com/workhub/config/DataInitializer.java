package com.workhub.config;

import com.workhub.entity.Tenant;
import com.workhub.entity.User;
import com.workhub.repository.TenantRepository;
import com.workhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Initializes test data for Phase 2 testing
 * Creates two tenants (ACME, Beta) with admin and user roles
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Only initialize if no tenants exist
        if (tenantRepository.count() == 0) {
            log.info("Initializing test data for Phase 2...");

            // Create ACME tenant
            Tenant acmeTenant = Tenant.builder()
                    .name("ACME Corp")
                    .plan("PRO")
                    .build();
            acmeTenant = tenantRepository.save(acmeTenant);
            log.info("Created ACME tenant: {}", acmeTenant.getTenantId());

            // Create Beta tenant
            Tenant betaTenant = Tenant.builder()
                    .name("Beta Inc")
                    .plan("FREE")
                    .build();
            betaTenant = tenantRepository.save(betaTenant);
            log.info("Created Beta tenant: {}", betaTenant.getTenantId());

            // Create ACME admin user
            User acmeAdmin = User.builder()
                    .email("admin@acme.com")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .tenantId(acmeTenant.getTenantId())
                    .roles(Set.of("TENANT_ADMIN", "TENANT_USER"))
                    .build();
            acmeAdmin = userRepository.save(acmeAdmin);
            log.info("Created ACME admin user: {}", acmeAdmin.getUserId());

            // Create ACME regular user
            User acmeUser = User.builder()
                    .email("user@acme.com")
                    .passwordHash(passwordEncoder.encode("user123"))
                    .tenantId(acmeTenant.getTenantId())
                    .roles(Set.of("TENANT_USER"))
                    .build();
            acmeUser = userRepository.save(acmeUser);
            log.info("Created ACME user: {}", acmeUser.getUserId());

            // Create Beta admin user
            User betaAdmin = User.builder()
                    .email("admin@beta.com")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .tenantId(betaTenant.getTenantId())
                    .roles(Set.of("TENANT_ADMIN", "TENANT_USER"))
                    .build();
            betaAdmin = userRepository.save(betaAdmin);
            log.info("Created Beta admin user: {}", betaAdmin.getUserId());

            // Create Beta regular user
            User betaUser = User.builder()
                    .email("user@beta.com")
                    .passwordHash(passwordEncoder.encode("user123"))
                    .tenantId(betaTenant.getTenantId())
                    .roles(Set.of("TENANT_USER"))
                    .build();
            betaUser = userRepository.save(betaUser);
            log.info("Created Beta user: {}", betaUser.getUserId());

            log.info("✓ Test data initialization complete");
        } else {
            log.info("Test data already exists, skipping initialization");
        }
    }
}
