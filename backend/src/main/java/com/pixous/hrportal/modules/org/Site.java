package com.pixous.hrportal.modules.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Civil construction site with its own configurable geofence (50–500m). */
@Getter
@Setter
@Entity
@Table(name = "sites")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 255)
    private String address;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "geofence_radius_metres")
    private Integer geofenceRadiusMetres = 200;

    @Column(name = "project_start")
    private LocalDate projectStart;

    @Column(name = "project_end")
    private LocalDate projectEnd;

    @Column(name = "site_manager_id")
    private Long siteManagerId;

    @Column(nullable = false)
    private boolean active = true;
}
