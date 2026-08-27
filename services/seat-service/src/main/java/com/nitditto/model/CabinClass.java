package com.nitditto.model;

import com.nitditto.enums.CabinClassType;
import jakarta.persistence.*;

public class CabinClass {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private CabinClassType name;

    @Column(nullable=false)
    private String code;

    private String description;

    @Column(nullable=false)
    private Long aircraftId;

    @Column(nullable=false)
    private Integer displayOrder = 0;

    @Column(nullable=false)
    private Boolean isActive = true;

    @Column(nullable=false)
    private Boolean isBookable=true;


}
