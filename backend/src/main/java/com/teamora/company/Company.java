package com.teamora.company;

import com.teamora.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** The employer / tenant. Every other tenant-scoped row belongs to one company. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** URL-safe unique handle, e.g. "lumi-foods". */
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "registration_no", length = 64)
    private String registrationNo;

    @Column(name = "epf_no", length = 64)
    private String epfNo;

    @Column(name = "socso_no", length = 64)
    private String socsoNo;

    private String email;
    private String phone;

    @Column(length = 512)
    private String address;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false)
    private boolean active;
}
