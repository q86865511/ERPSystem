package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    boolean existsByCode(String code);

    List<Equipment> findByActiveTrueOrderByCode();
}
