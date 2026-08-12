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

@Getter
@Setter
@Entity
@Table(name = "office_locations")
public class OfficeLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "geofence_radius_metres")
    private Integer geofenceRadiusMetres = 200;

    @Column(nullable = false)
    private boolean active = true;
}
