package com.workhub.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tenantId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String plan; // e.g. FREE, PRO
}