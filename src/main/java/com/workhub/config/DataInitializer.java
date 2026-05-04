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
 * Creates two tenants (ACME, TechFlow) with admin and user roles
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

            // Create TechFlow tenant
            Tenant techflowTenant = Tenant.builder()
                    .name("TechFlow Inc")
                    .plan("FREE")
                    .build();
            techflowTenant = tenantRepository.save(techflowTenant);
            log.info("Created TechFlow tenant: {}", techflowTenant.getTenantId());

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

            // Create TechFlow admin user
            User techflowAdmin = User.builder()
                    .email("admin@techflow.com")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .tenantId(techflowTenant.getTenantId())
                    .roles(Set.of("TENANT_ADMIN", "TENANT_USER"))
                    .build();
            techflowAdmin = userRepository.save(techflowAdmin);
            log.info("Created TechFlow admin user: {}", techflowAdmin.getUserId());

            // Create TechFlow regular user
            User techflowUser = User.builder()
                    .email("user@techflow.com")
                    .passwordHash(passwordEncoder.encode("user123"))
                    .tenantId(techflowTenant.getTenantId())
                    .roles(Set.of("TENANT_USER"))
                    .build();
            techflowUser = userRepository.save(techflowUser);
            log.info("Created TechFlow user: {}", techflowUser.getUserId());

            log.info("✓ Test data initialization complete");
        } else {
            log.info("Test data already exists, skipping initialization");
        }
    }
}
