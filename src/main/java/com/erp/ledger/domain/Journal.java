package com.erp.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * A journal (book of entry). Phase 0 uses a single GENERAL journal; the table and reference exist so
 * adding Sales/Purchase/Cash/Manufacturing books later is a non-breaking change.
 */
@Entity
@Table(name = "journal")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;
}
