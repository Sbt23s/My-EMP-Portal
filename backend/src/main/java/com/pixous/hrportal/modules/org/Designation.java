package com.pixous.hrportal.modules.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "designations")
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String code;

    /** IT | CIVIL — drives the industry-specific designation dropdown. */
    @Column(length = 20)
    private String industry = "IT";

    @Column(nullable = false)
    private boolean active = true;
}
