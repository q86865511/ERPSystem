package com.erp.manufacturing.application;

import com.erp.manufacturing.domain.Equipment;
import com.erp.manufacturing.domain.ProductionLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side OEE analytics for the manufacturing dashboard: per-equipment Availability / Performance /
 * Quality / OEE aggregated from the daily production logs, plus a downtime-by-reason breakdown. All values
 * are percentages (0–100).
 */
@Service
@Transactional(readOnly = true)
public class OeeService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EquipmentRepository equipmentRepository;
    private final ProductionLogRepository productionLogRepository;

    public OeeService(EquipmentRepository equipmentRepository,
                      ProductionLogRepository productionLogRepository) {
        this.equipmentRepository = equipmentRepository;
        this.productionLogRepository = productionLogRepository;
    }

    public List<EquipmentOee> oee() {
        List<EquipmentOee> result = new ArrayList<>();
        for (Equipment equipment : equipmentRepository.findByActiveTrueOrderByCode()) {
            List<ProductionLog> logs = productionLogRepository.findByEquipmentId(equipment.getId());
            long planned = 0;
            long downtime = 0;
            long produced = 0;
            long good = 0;
            for (ProductionLog log : logs) {
                planned += log.getPlannedMinutes();
                downtime += log.getDowntimeMinutes();
                produced += log.getProducedUnits();
                good += log.getGoodUnits();
            }
            long runtime = planned - downtime;

            BigDecimal availability = pct(BigDecimal.valueOf(runtime), BigDecimal.valueOf(planned));
            // Ideal output = ideal units/hour × run-time hours.
            BigDecimal idealOutput = equipment.getIdealUnitsPerHour()
                    .multiply(BigDecimal.valueOf(runtime))
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            BigDecimal performance = pct(BigDecimal.valueOf(produced), idealOutput);
            BigDecimal quality = pct(BigDecimal.valueOf(good), BigDecimal.valueOf(produced));
            BigDecimal oee = availability.multiply(performance).multiply(quality)
                    .divide(new BigDecimal("10000"), 1, RoundingMode.HALF_UP);

            result.add(new EquipmentOee(equipment.getId(), equipment.getCode(), equipment.getName(),
                    availability, performance, quality, oee));
        }
        return result;
    }

    public List<DowntimeReason> downtime() {
        Map<String, Long> byReason = new LinkedHashMap<>();
        for (ProductionLog log : productionLogRepository.findAll()) {
            if (log.getDowntimeReason() != null && log.getDowntimeMinutes() > 0) {
                byReason.merge(log.getDowntimeReason(), (long) log.getDowntimeMinutes(), Long::sum);
            }
        }
        List<DowntimeReason> result = new ArrayList<>();
        byReason.forEach((reason, minutes) -> result.add(new DowntimeReason(reason, minutes)));
        result.sort(Comparator.comparingLong(DowntimeReason::minutes).reversed());
        return result;
    }

    /** part/whole as a percentage (0–100, one decimal), capped at 100 and floored at 0. */
    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(HUNDRED);
    }
}
