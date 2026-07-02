package com.erp.bootstrap;

import com.erp.hr.api.AttendanceStatus;
import com.erp.hr.api.EmploymentStatus;
import com.erp.hr.api.LeaveType;
import com.erp.hr.application.AttendanceService;
import com.erp.hr.application.HrService;
import com.erp.hr.application.LeaveService;
import com.erp.hr.application.PayrollService;
import com.erp.hr.application.TimesheetService;
import com.erp.manufacturing.application.BomService;
import com.erp.manufacturing.application.BomService.ComponentInput;
import com.erp.manufacturing.application.EquipmentRepository;
import com.erp.manufacturing.application.ProductionLogRepository;
import com.erp.manufacturing.application.WorkOrderService;
import com.erp.manufacturing.domain.BillOfMaterials;
import com.erp.manufacturing.domain.Equipment;
import com.erp.manufacturing.domain.ProductionLog;
import com.erp.manufacturing.domain.WorkOrder;
import com.erp.masterdata.api.ItemType;
import com.erp.masterdata.api.LocationType;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.masterdata.application.LocationRepository;
import com.erp.masterdata.application.MasterDataService;
import com.erp.masterdata.application.WarehouseRepository;
import com.erp.payments.application.PaymentService;
import com.erp.payments.application.PaymentService.Allocation;
import com.erp.payments.application.PaymentService.ReceiptAllocation;
import com.erp.purchasing.application.GoodsReceiptService;
import com.erp.purchasing.application.GoodsReceiptService.ReceiptLineInput;
import com.erp.purchasing.application.PurchaseOrderService;
import com.erp.purchasing.application.PurchaseOrderService.PoLineInput;
import com.erp.purchasing.application.VendorBillService;
import com.erp.purchasing.application.VendorBillService.BillLineInput;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.VendorBill;
import com.erp.reporting.application.BudgetRepository;
import com.erp.reporting.domain.Budget;
import com.erp.sales.application.DeliveryService;
import com.erp.sales.application.DeliveryService.DeliveryLineInput;
import com.erp.sales.application.SalesInvoiceService;
import com.erp.sales.application.SalesInvoiceService.InvoiceLineInput;
import com.erp.sales.application.SalesOrderService;
import com.erp.sales.application.SalesOrderService.SoLineInput;
import com.erp.sales.domain.Delivery;
import com.erp.sales.domain.SalesInvoice;
import com.erp.sales.domain.SalesOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One-key demo seed (profile {@code seed}). Runs the full buy → make → sell slice through the real
 * posting services — so the books land balanced exactly as they would in production — rather than bypassing
 * the invariants with raw SQL. After it runs, the reconciliation health-check is healthy: GR-IR, AP,
 * Deferred-COGS, AR and WIP have all netted to zero, and inventory/COGS/revenue are booked.
 *
 * <p>On top of the canonical single cycle (RM-DEMO/FG-DEMO), it seeds a richer, multi-month, multi-item
 * data set so the dashboards render with real depth: a spread of purchase/sales orders across the past
 * months of FY2026, several product families, work orders in mixed states, and — crucially — some
 * invoiced-but-unpaid documents that fill the AR/AP aging buckets. Every document still flows through the
 * real services, so the reconciliation stays green: any receive is billed, any issue is completed, any
 * delivery is invoiced. Documents left "in progress" stop only at pre-posting states (DRAFT/CONFIRMED for
 * orders, RELEASED for work orders), which touch no clearing account.
 *
 * <p>This is the application composition root wiring every module's services, so it lives outside the
 * per-module boundaries (it is not part of any business module).
 */
@Component
@Profile("seed")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String ACTOR = "seed";
    private static final String VENDOR_CODE = "VEND-DEMO";
    private static final String TAX_CODE = "STANDARD";

    private final HrService hrService;
    private final AttendanceService attendanceService;
    private final LeaveService leaveService;
    private final TimesheetService timesheetService;
    private final PayrollService payrollService;
    private final BudgetRepository budgetRepository;
    private final EquipmentRepository equipmentRepository;
    private final ProductionLogRepository productionLogRepository;
    private final MasterDataService masterDataService;
    private final MasterDataQuery masterDataQuery;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;
    private final VendorBillService vendorBillService;
    private final PaymentService paymentService;
    private final BomService bomService;
    private final WorkOrderService workOrderService;
    private final SalesOrderService salesOrderService;
    private final DeliveryService deliveryService;
    private final SalesInvoiceService salesInvoiceService;

    public DataSeeder(MasterDataService masterDataService, MasterDataQuery masterDataQuery,
                      WarehouseRepository warehouseRepository, LocationRepository locationRepository,
                      PurchaseOrderService purchaseOrderService, GoodsReceiptService goodsReceiptService,
                      VendorBillService vendorBillService, PaymentService paymentService,
                      BomService bomService, WorkOrderService workOrderService,
                      SalesOrderService salesOrderService, DeliveryService deliveryService,
                      SalesInvoiceService salesInvoiceService, HrService hrService,
                      AttendanceService attendanceService, LeaveService leaveService,
                      TimesheetService timesheetService, PayrollService payrollService,
                      BudgetRepository budgetRepository, EquipmentRepository equipmentRepository,
                      ProductionLogRepository productionLogRepository) {
        this.hrService = hrService;
        this.attendanceService = attendanceService;
        this.leaveService = leaveService;
        this.timesheetService = timesheetService;
        this.payrollService = payrollService;
        this.budgetRepository = budgetRepository;
        this.equipmentRepository = equipmentRepository;
        this.productionLogRepository = productionLogRepository;
        this.masterDataService = masterDataService;
        this.masterDataQuery = masterDataQuery;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.goodsReceiptService = goodsReceiptService;
        this.vendorBillService = vendorBillService;
        this.paymentService = paymentService;
        this.bomService = bomService;
        this.workOrderService = workOrderService;
        this.salesOrderService = salesOrderService;
        this.salesInvoiceService = salesInvoiceService;
        this.deliveryService = deliveryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (masterDataQuery.findPartnerByCode(VENDOR_CODE).isPresent()) {
            log.info("Demo data already present; skipping seed.");
            return;
        }
        LocalDate today = LocalDate.now();
        Long stock = stockLocationId();
        log.info("Seeding demo buy -> make -> sell slice...");

        List<Long> employeeIds = seedHumanResources();
        seedHrTime(employeeIds, today);

        Long vendor = masterDataService.createPartner(VENDOR_CODE, "Demo Vendor", true, false, null, 30,
                null, null).getId();
        Long customer = masterDataService.createPartner("CUST-DEMO", "Demo Customer", false, true, null,
                30, null, null).getId();
        Long raw = masterDataService.createItem("RM-DEMO", "Demo Raw Material", ItemType.RAW, "EA", true,
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("100")).getId();
        Long finished = masterDataService.createItem("FG-DEMO", "Demo Finished Good", ItemType.FINISHED,
                "EA", true, BigDecimal.ZERO, null, null).getId();

        // BUY: PO 100 @ 10 -> receive -> bill (+5% VAT) -> pay.
        PurchaseOrder po = purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(raw, new BigDecimal("100"), new BigDecimal("10"))), today, ACTOR);
        purchaseOrderService.confirm(po.getId(), ACTOR);
        Long poLine = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stock,
                List.of(new ReceiptLineInput(poLine, new BigDecimal("100"))), today, ACTOR);
        VendorBill bill = vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(poLine, new BigDecimal("100"), new BigDecimal("10"))),
                TAX_CODE, today, ACTOR);
        paymentService.payOut(vendor, bill.getGrossAmount(), today,
                List.of(new Allocation(bill.getId(), bill.getGrossAmount())), ACTOR);

        // MAKE: single-level BOM (1 FG <- 1 RM) -> work order 50 -> release -> issue -> complete.
        BillOfMaterials bom = bomService.createBom(finished, new BigDecimal("1"),
                List.of(new ComponentInput(raw, new BigDecimal("1"), null)), ACTOR);
        WorkOrder wo = workOrderService.create(finished, bom.getId(), new BigDecimal("50"), ACTOR);
        workOrderService.release(wo.getId(), ACTOR);
        workOrderService.issue(wo.getId(), stock, today, ACTOR);
        workOrderService.complete(wo.getId(), new BigDecimal("50"), stock, today, ACTOR);

        // SELL: SO 30 @ 20 -> deliver -> invoice (+5% VAT) -> receive payment.
        SalesOrder so = salesOrderService.createOrder(customer,
                List.of(new SoLineInput(finished, new BigDecimal("30"), new BigDecimal("20"))), today,
                ACTOR);
        salesOrderService.confirm(so.getId(), ACTOR);
        Long soLine = so.getLines().get(0).getId();
        Delivery delivery = deliveryService.deliver(so.getId(), stock,
                List.of(new DeliveryLineInput(soLine, new BigDecimal("30"))), today, ACTOR);
        SalesInvoice invoice = salesInvoiceService.postInvoice(so.getId(),
                List.of(new InvoiceLineInput(soLine, new BigDecimal("30"), new BigDecimal("20"))),
                TAX_CODE, today, ACTOR);
        paymentService.payIn(customer, invoice.getGrossAmount(), today,
                List.of(new ReceiptAllocation(invoice.getId(), invoice.getGrossAmount())), ACTOR);

        log.info("Canonical seed complete: PO {}, WO {}, delivery {}, invoice {} — books reconcile.",
                po.getPoNumber(), wo.getWoNumber(), delivery.getDeliveryNumber(),
                invoice.getInvoiceNumber());

        seedRichDemoData(stock, today);
        seedBudgets(today.getYear());
        seedEquipmentOee(today);
    }

    /** Seed a few machines and ~10 recent production-log days each, so the OEE dashboard has data. */
    private void seedEquipmentOee(LocalDate today) {
        record Machine(String code, String name, String idealPerHour) {}
        List<Machine> machines = List.of(
                new Machine("EQ-CNC-1", "CNC Mill #1", "30"),
                new Machine("EQ-LATHE-1", "CNC Lathe #1", "24"),
                new Machine("EQ-PRESS-1", "Hydraulic Press #1", "40"));
        String[] reasons = {"Setup", "Maintenance", "Breakdown", "Material shortage"};

        List<LocalDate> days = new ArrayList<>();
        LocalDate day = today.minusDays(1);
        while (days.size() < 10) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(day);
            }
            day = day.minusDays(1);
        }

        int machineIndex = 0;
        for (Machine machine : machines) {
            Equipment equipment = equipmentRepository.save(
                    new Equipment(machine.code(), machine.name(), new BigDecimal(machine.idealPerHour())));
            BigDecimal idealPerHour = equipment.getIdealUnitsPerHour();
            int d = 0;
            for (LocalDate logDate : days) {
                int planned = 480;
                int downtime = 20 + ((machineIndex + d) % 5) * 10;   // 20..60 minutes
                String reason = reasons[(machineIndex + d) % reasons.length];
                int runtime = planned - downtime;
                int idealOutput = idealPerHour.multiply(BigDecimal.valueOf(runtime))
                        .divide(BigDecimal.valueOf(60), 0, java.math.RoundingMode.HALF_UP).intValue();
                int produced = (int) Math.round(idealOutput * (0.85 + (d % 3) * 0.04));   // ~85–93%
                int good = (int) Math.round(produced * (0.93 + (d % 2) * 0.03));           // ~93–96%
                productionLogRepository.save(new ProductionLog(equipment.getId(), logDate, planned,
                        downtime, reason, produced, good));
                d++;
            }
            machineIndex++;
        }
        log.info("OEE seed complete: {} machines x {} production days.", machines.size(), days.size());
    }

    /** Seed a monthly budget for the key P&L accounts so the budget-variance report has targets. */
    private void seedBudgets(int year) {
        record BudgetLine(String account, String amount) {}
        List<BudgetLine> budgets = List.of(
                new BudgetLine("4100", "500000"),   // Sales revenue (aligned to the 10x sale prices)
                new BudgetLine("5100", "150000"),   // COGS
                new BudgetLine("6100", "560000"));  // Salaries expense
        for (BudgetLine b : budgets) {
            budgetRepository.save(new Budget(year, b.account(), new BigDecimal(b.amount())));
        }
        log.info("Budget seed complete: {} account budgets for {}.", budgets.size(), year);
    }

    private Long stockLocationId() {
        Long warehouseId = warehouseRepository.findByCode("WH1").orElseThrow().getId();
        return locationRepository.findByWarehouseIdAndLocationType(warehouseId, LocationType.STOCK)
                .orElseThrow().getId();
    }

    /** Seed a small org chart (3 departments, 4 positions, 8 employees); returns the employee ids. */
    private List<Long> seedHumanResources() {
        Long eng = hrService.createDepartment("ENG", "Engineering", "6100").getId();
        Long ops = hrService.createDepartment("OPS", "Operations", "6100").getId();
        Long adm = hrService.createDepartment("ADM", "Finance & Admin", "6100").getId();

        Long engineer = hrService.createPosition("ENGINEER", "Engineer", new BigDecimal("70000")).getId();
        Long manager = hrService.createPosition("MANAGER", "Manager", new BigDecimal("95000")).getId();
        Long operator = hrService.createPosition("OPERATOR", "Operator", new BigDecimal("42000")).getId();
        Long accountant = hrService.createPosition("ACCOUNTANT", "Accountant", new BigDecimal("60000")).getId();

        record Emp(String code, String first, String last, Long dept, Long pos, String salary, int year) {}
        List<Emp> roster = List.of(
                new Emp("EMP-001", "Wei-Lun", "Chou", eng, manager, "98000", 2020),
                new Emp("EMP-002", "Mei", "Lin", eng, engineer, "72000", 2021),
                new Emp("EMP-003", "Cheng", "Wang", eng, engineer, "68000", 2022),
                new Emp("EMP-004", "Ya-Ting", "Chen", ops, manager, "90000", 2019),
                new Emp("EMP-005", "Jun", "Huang", ops, operator, "44000", 2022),
                new Emp("EMP-006", "Hsin", "Liu", ops, operator, "41000", 2023),
                new Emp("EMP-007", "Pei", "Yang", adm, accountant, "61000", 2021),
                new Emp("EMP-008", "Kai", "Wu", adm, accountant, "58000", 2023));
        List<Long> employeeIds = new ArrayList<>();
        for (Emp e : roster) {
            employeeIds.add(hrService.createEmployee(e.code(), e.first(), e.last(), e.dept(), e.pos(),
                    new BigDecimal(e.salary()), EmploymentStatus.ACTIVE,
                    LocalDate.of(e.year(), 4, 1)).getId());
        }
        log.info("HR seed complete: 3 departments, 4 positions, {} employees.", roster.size());
        return employeeIds;
    }

    /**
     * Seed HR time data (B2) for the employees: a month of daily attendance, a spread of leave requests
     * across the lifecycle (some left PENDING for the approve/reject demo), and weekly timesheets in mixed
     * states. None of this posts to the ledger, so it never touches the reconciliation.
     */
    private void seedHrTime(List<Long> employeeIds, LocalDate today) {
        // The most recent ~18 weekdays ending today (spans the last few weeks regardless of the date).
        List<LocalDate> workdays = new ArrayList<>();
        LocalDate day = today;
        while (workdays.size() < 18) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workdays.add(day);
            }
            day = day.minusDays(1);
        }
        // Daily attendance: mostly present, with a deterministic sprinkle of late/remote/leave.
        for (int e = 0; e < employeeIds.size(); e++) {
            for (LocalDate d : workdays) {
                int n = e * 31 + d.getDayOfMonth();
                AttendanceStatus status = n % 19 == 0 ? AttendanceStatus.LEAVE
                        : n % 11 == 0 ? AttendanceStatus.REMOTE
                        : n % 7 == 0 ? AttendanceStatus.LATE
                        : AttendanceStatus.PRESENT;
                BigDecimal hours = status == AttendanceStatus.LEAVE ? BigDecimal.ZERO
                        : status == AttendanceStatus.LATE ? new BigDecimal("7") : new BigDecimal("8");
                attendanceService.record(employeeIds.get(e), d, status, hours, null);
            }
        }

        // Leave requests across the lifecycle — a couple left PENDING for the approve/reject demo.
        LocalDate leaveStart = today.plusDays(7);
        leaveService.submit(employeeIds.get(1), LeaveType.ANNUAL, leaveStart, leaveStart.plusDays(2),
                new BigDecimal("3"), "Family trip");                                    // PENDING
        leaveService.submit(employeeIds.get(4), LeaveType.PERSONAL, leaveStart.plusDays(3),
                leaveStart.plusDays(3), new BigDecimal("1"), "Personal errand");        // PENDING
        leaveService.approve(leaveService.submit(employeeIds.get(2), LeaveType.SICK,
                today.minusDays(3), today.minusDays(2), new BigDecimal("2"), "Flu").getId(), ACTOR);
        leaveService.approve(leaveService.submit(employeeIds.get(0), LeaveType.ANNUAL,
                today.minusDays(20), today.minusDays(16), new BigDecimal("5"), "Vacation").getId(), ACTOR);
        leaveService.reject(leaveService.submit(employeeIds.get(5), LeaveType.UNPAID,
                today.plusDays(30), today.plusDays(44), new BigDecimal("15"), "Sabbatical").getId(), ACTOR);

        // Weekly timesheets in mixed states for a few employees over the last three weeks.
        LocalDate friday = today;
        while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
            friday = friday.minusDays(1);
        }
        List<LocalDate> weeks = List.of(friday, friday.minusWeeks(1), friday.minusWeeks(2));
        for (int e = 0; e < 4 && e < employeeIds.size(); e++) {
            Long emp = employeeIds.get(e);
            // Oldest week approved, middle week submitted, current week left as a draft.
            Long approved = timesheetService.create(emp, weeks.get(2), new BigDecimal("40"),
                    new BigDecimal(e + 1), null).getId();
            timesheetService.submit(approved);
            timesheetService.approve(approved);
            timesheetService.submit(timesheetService.create(emp, weeks.get(1), new BigDecimal("40"),
                    new BigDecimal("2"), null).getId());
            timesheetService.create(emp, weeks.get(0), new BigDecimal("38"), BigDecimal.ZERO, null);
        }

        // Run and post last month's payroll: one balanced JE to the GL (Dr 6100 / Cr 2200+2210+2220).
        // Only GL accounts are touched, so the reconciliation stays green. Fall back to the current month
        // if the previous month falls outside FY2026's open periods.
        LocalDate lastMonth = today.minusMonths(1);
        int payYear = lastMonth.getYear() == 2026 ? lastMonth.getYear() : today.getYear();
        int payMonth = lastMonth.getYear() == 2026 ? lastMonth.getMonthValue() : today.getMonthValue();
        Long payrollId = payrollService.run(payYear, payMonth).getId();
        payrollService.post(payrollId, today, ACTOR);

        log.info("HR time seed complete: attendance, leave, timesheets and a posted payroll for {} "
                + "employees.", employeeIds.size());
    }

    // =================================================================================================
    // Rich demo enrichment — multi-month, multi-item buy → make → sell so the dashboards render with
    // depth. Everything flows through the real services and obeys the reconciliation invariants:
    //   • every receive is billed, every issue is completed, every delivery is invoiced (clearing → 0);
    //   • "in progress" documents stop only at DRAFT/CONFIRMED (orders) or RELEASED (work orders);
    //   • some invoiced-but-unpaid documents are left open, back-dated across the year, to populate the
    //     AR/AP aging buckets (subledger still equals the GL control account, so the books reconcile).
    // =================================================================================================

    /** Finished good plus its (already created) bill of materials and demo sale price. */
    private record Family(Long finished, Long bomId, String salePrice) {}

    private void seedRichDemoData(Long stock, LocalDate today) {
        // Posting dates spread over the past months of FY2026 (all twelve periods are seeded OPEN). The
        // 15th of each earlier month, plus today, lets back-dated unpaid invoices land across every aging
        // bucket. Falls back to just today outside 2026 so we never post into a missing/closed period.
        List<LocalDate> months = new ArrayList<>();
        if (today.getYear() == 2026) {
            for (int m = 1; m < today.getMonthValue(); m++) {
                months.add(LocalDate.of(2026, m, 15));
            }
        }
        months.add(today);

        // --- Master data -----------------------------------------------------------------------------
        List<Long> vendors = List.of(
                vendor("VEND-STEEL", "Formosa Steel Co."),
                vendor("VEND-ALLOY", "Pacific Alloy Ltd."),
                vendor("VEND-POLY", "Taipei Polymers Inc."),
                vendor("VEND-ELEC", "Hsinchu Electronics"),
                vendor("VEND-PKG", "Kaohsiung Packaging"));
        List<Long> customers = List.of(
                customer("CUST-NORTH", "Northern Machinery"),
                customer("CUST-SOUTH", "Southern Industries"),
                customer("CUST-EAST", "Eastern Automation"),
                customer("CUST-WEST", "Western Robotics"),
                customer("CUST-GLOBAL", "Global Distributors"));

        // Production raw materials — bought generously so issues never exceed on-hand.
        Long rSteel = rawItem("RM-STEEL", "Steel Sheet", "150", null, null);
        Long rAlu = rawItem("RM-ALU", "Aluminium Bar", "120", null, null);
        Long rPoly = rawItem("RM-POLY", "Polymer Resin", "80", null, null);
        Long rRubber = rawItem("RM-RUBBER", "Rubber Seal", "15", null, null);
        Long rPcb = rawItem("RM-PCB", "PCB Board", "200", null, null);
        // Low-stock demo materials — a reorder point with a small on-hand, so they surface on the reorder
        // report / low-stock alert. They must carry a small purchase (below) so an on-hand row exists: the
        // reorder report is driven by inventory movements, so a never-received item would never appear.
        Long rCopper = rawItem("RM-COPPER", "Copper Wire", "60", "100", "200");
        Long rGlass = rawItem("RM-GLASS", "Glass Panel", "90", "50", "150");
        Long rLube = rawItem("RM-LUBE", "Lubricant Oil", "30", "80", "120");

        // Finished goods (their cost comes from the production roll-up, so standard cost is 0).
        Long fPump = finishedItem("FG-PUMP", "Hydraulic Pump", null);
        Long fValve = finishedItem("FG-VALVE", "Control Valve", null);
        Long fMotor = finishedItem("FG-MOTOR", "Servo Motor", null);
        Long fSensor = finishedItem("FG-SENSOR", "Pressure Sensor", null);
        Long fPanel = finishedItem("FG-PANEL", "Control Panel", null);

        // --- Purchasing: fully-received-and-paid raw, spread over months (no AP left open here) --------
        buyAndPay(vendors.get(0), rSteel, "220", "150", monthAt(months, 0), stock);
        buyAndPay(vendors.get(0), rSteel, "200", "156", monthAt(months, 2), stock);
        buyAndPay(vendors.get(1), rAlu, "280", "120", monthAt(months, 0), stock);
        buyAndPay(vendors.get(1), rAlu, "260", "124", monthAt(months, 3), stock);
        buyAndPay(vendors.get(2), rPoly, "220", "80", monthAt(months, 1), stock);
        buyAndPay(vendors.get(2), rPoly, "200", "83", monthAt(months, 3), stock);
        buyAndPay(vendors.get(3), rPcb, "220", "200", monthAt(months, 1), stock);
        buyAndPay(vendors.get(3), rPcb, "200", "205", monthAt(months, 4), stock);
        buyAndPay(vendors.get(0), rRubber, "200", "15", monthAt(months, 2), stock);
        // Small purchases for the low-stock demo materials — an on-hand row exists but stays below reorder.
        buyAndPay(vendors.get(4), rGlass, "10", "90", monthAt(months, 1), stock);
        buyAndPay(vendors.get(3), rCopper, "20", "60", monthAt(months, 2), stock);
        buyAndPay(vendors.get(4), rLube, "15", "30", monthAt(months, 3), stock);

        // --- Supplier on-time performance: POs with an expected delivery date, received on-time or late.
        LocalDate schedOrder = monthAt(months, 0);
        buyWithSchedule(vendors.get(0), rSteel, "40", "150", schedOrder, schedOrder.plusDays(7),
                schedOrder.plusDays(5), stock);   // on time
        buyWithSchedule(vendors.get(0), rSteel, "30", "152", schedOrder, schedOrder.plusDays(7),
                schedOrder.plusDays(12), stock);  // late
        buyWithSchedule(vendors.get(1), rAlu, "45", "120", schedOrder, schedOrder.plusDays(10),
                schedOrder.plusDays(6), stock);   // on time
        buyWithSchedule(vendors.get(1), rAlu, "20", "121", schedOrder, schedOrder.plusDays(6),
                schedOrder.plusDays(6), stock);   // on time (met exactly)
        buyWithSchedule(vendors.get(2), rPoly, "30", "80", schedOrder, schedOrder.plusDays(5),
                schedOrder.plusDays(9), stock);   // late
        buyWithSchedule(vendors.get(3), rPcb, "40", "200", schedOrder, schedOrder.plusDays(8),
                schedOrder.plusDays(3), stock);   // on time

        // --- Purchasing: received-and-billed but UNPAID, back-dated -> fills the AP aging buckets ------
        Long[] apRaw = {rSteel, rAlu, rPoly, rPcb, rRubber, rSteel};
        for (int i = 0; i < months.size(); i++) {
            buyOnCredit(vendors.get(i % vendors.size()), apRaw[i % apRaw.length], "40", "160",
                    months.get(i), stock);
        }

        // --- Purchasing: orders still open (no receipt) -> "purchasing in progress" has volume ---------
        purchaseOrderService.confirm(draftPo(vendors.get(2), rPoly, "120", "82", today).getId(), ACTOR);
        purchaseOrderService.confirm(draftPo(vendors.get(3), rPcb, "60", "203", today).getId(), ACTOR);
        draftPo(vendors.get(1), rAlu, "90", "122", today); // stays DRAFT

        // --- Bills of material (one per finished good, component qty <= 2) -----------------------------
        List<Family> families = List.of(
                new Family(fPump, bom(fPump, List.of(comp(rSteel, "2"), comp(rRubber, "1"))), "6200"),
                new Family(fValve, bom(fValve, List.of(comp(rSteel, "1"), comp(rAlu, "1"))), "4300"),
                new Family(fMotor, bom(fMotor, List.of(comp(rAlu, "2"), comp(rPcb, "1"))), "7800"),
                new Family(fSensor, bom(fSensor, List.of(comp(rPcb, "2"), comp(rPoly, "1"))), "5600"),
                new Family(fPanel, bom(fPanel, List.of(comp(rPoly, "2"), comp(rAlu, "1"))), "5100"));

        // --- Manufacturing: two completed work orders per family (produce finished goods) --------------
        for (Family f : families) {
            produce(f.finished(), f.bomId(), "50", stock, monthAt(months, 4));
            produce(f.finished(), f.bomId(), "40", stock, monthAt(months, 5));
        }
        // Released work orders (not issued) -> WIP KPI, progress board and dispatch queue have data.
        releaseWorkOrder(fPump, families.get(0).bomId(), "30", today.minusDays(2), today.plusDays(5));
        releaseWorkOrder(fValve, families.get(1).bomId(), "25", today.plusDays(1), today.plusDays(8));
        releaseWorkOrder(fMotor, families.get(2).bomId(), "45", today.plusDays(3), today.plusDays(11));
        releaseWorkOrder(fSensor, families.get(3).bomId(), "20", today.plusDays(6), today.plusDays(12));
        releaseWorkOrder(fPanel, families.get(4).bomId(), "35", today.plusDays(8), today.plusDays(16));
        // Draft work orders (scheduled further out) -> dispatch queue + schedule Gantt depth.
        workOrderService.create(fPump, families.get(0).bomId(), new BigDecimal("15"),
                today.plusDays(12), today.plusDays(19), ACTOR);
        workOrderService.create(fMotor, families.get(2).bomId(), new BigDecimal("10"),
                today.plusDays(14), today.plusDays(22), ACTOR);

        // --- Sales: delivered-invoiced-and-paid, spread over recent months (revenue/COGS, no AR) -------
        for (int i = 0; i < families.size(); i++) {
            Family f = families.get(i);
            sellAndCollect(customers.get(i % customers.size()), f.finished(), "20", f.salePrice(),
                    monthAt(months, 4), stock);
            sellAndCollect(customers.get((i + 1) % customers.size()), f.finished(), "15", f.salePrice(),
                    monthAt(months, 5), stock);
        }

        // --- Sales: delivered-and-invoiced but UNPAID, back-dated -> fills the AR aging buckets --------
        for (int i = 0; i < months.size(); i++) {
            Family f = families.get(i % families.size());
            sellOnCredit(customers.get(i % customers.size()), f.finished(), "8", f.salePrice(),
                    months.get(i), stock);
        }

        // --- Sales: orders still open -> the order funnel has confirmed and draft stages ---------------
        salesOrderService.confirm(draftSo(customers.get(0), fPump, "12", "6200", today).getId(), ACTOR);
        salesOrderService.confirm(draftSo(customers.get(1), fValve, "10", "4300", today).getId(), ACTOR);
        salesOrderService.confirm(draftSo(customers.get(2), fMotor, "6", "7800", today).getId(), ACTOR);
        salesOrderService.confirm(draftSo(customers.get(3), fPanel, "9", "5100", today).getId(), ACTOR);
        draftSo(customers.get(4), fSensor, "8", "5600", today);   // stays DRAFT
        draftSo(customers.get(0), fMotor, "5", "7800", today);    // stays DRAFT

        log.info("Rich demo seed complete: {} vendors, {} customers, {} product families across {} months.",
                vendors.size(), customers.size(), families.size(), months.size());
    }

    // ---- Master-data helpers ------------------------------------------------------------------------

    private Long vendor(String code, String name) {
        return masterDataService.createPartner(code, name, true, false, null, 30, null, null).getId();
    }

    private Long customer(String code, String name) {
        return masterDataService.createPartner(code, name, false, true, null, 30, null, null).getId();
    }

    private Long rawItem(String sku, String name, String standardCost, String reorderPoint,
                         String reorderQty) {
        return masterDataService.createItem(sku, name, ItemType.RAW, "EA", true,
                new BigDecimal(standardCost),
                reorderPoint == null ? null : new BigDecimal(reorderPoint),
                reorderQty == null ? null : new BigDecimal(reorderQty)).getId();
    }

    private Long finishedItem(String sku, String name, String reorderPoint) {
        return masterDataService.createItem(sku, name, ItemType.FINISHED, "EA", true, BigDecimal.ZERO,
                reorderPoint == null ? null : new BigDecimal(reorderPoint), null).getId();
    }

    private ComponentInput comp(Long itemId, String qtyPer) {
        return new ComponentInput(itemId, new BigDecimal(qtyPer), null);
    }

    private Long bom(Long finished, List<ComponentInput> components) {
        return bomService.createBom(finished, BigDecimal.ONE, components, ACTOR).getId();
    }

    // ---- Purchasing helpers -------------------------------------------------------------------------

    /** PO (with an expected delivery date) → confirm → receive on {@code receiveDate} → bill → pay. Drives
     *  the supplier on-time report: on time iff the receive date is on or before the expected date. */
    private void buyWithSchedule(Long vendor, Long item, String qty, String price, LocalDate orderDate,
                                 LocalDate expectedDate, LocalDate receiveDate, Long stock) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        PurchaseOrder po = purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(item, q, p, expectedDate)), orderDate, ACTOR);
        purchaseOrderService.confirm(po.getId(), ACTOR);
        Long line = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stock, List.of(new ReceiptLineInput(line, q)),
                receiveDate, ACTOR);
        VendorBill bill = vendorBillService.postBill(po.getId(),
                List.of(new BillLineInput(line, q, p)), TAX_CODE, receiveDate, ACTOR);
        paymentService.payOut(vendor, bill.getGrossAmount(), receiveDate,
                List.of(new Allocation(bill.getId(), bill.getGrossAmount())), ACTOR);
    }

    /** PO → confirm → receive → bill (+VAT) → pay in full. Adds inventory, leaves no AP open. */
    private void buyAndPay(Long vendor, Long item, String qty, String price, LocalDate date, Long stock) {
        VendorBill posted = receiveAndBill(vendor, item, qty, price, date, stock);
        paymentService.payOut(vendor, posted.getGrossAmount(), date,
                List.of(new Allocation(posted.getId(), posted.getGrossAmount())), ACTOR);
    }

    /** PO → confirm → receive → bill (+VAT), left unpaid. Adds inventory and an open AP balance. */
    private void buyOnCredit(Long vendor, Long item, String qty, String price, LocalDate date, Long stock) {
        receiveAndBill(vendor, item, qty, price, date, stock);
    }

    private VendorBill receiveAndBill(Long vendor, Long item, String qty, String price, LocalDate date,
                                      Long stock) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        PurchaseOrder po = purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(item, q, p)), date, ACTOR);
        purchaseOrderService.confirm(po.getId(), ACTOR);
        Long line = po.getLines().get(0).getId();
        goodsReceiptService.receive(po.getId(), stock, List.of(new ReceiptLineInput(line, q)), date, ACTOR);
        return vendorBillService.postBill(po.getId(), List.of(new BillLineInput(line, q, p)), TAX_CODE,
                date, ACTOR);
    }

    private PurchaseOrder draftPo(Long vendor, Long item, String qty, String price, LocalDate date) {
        return purchaseOrderService.createOrder(vendor,
                List.of(new PoLineInput(item, new BigDecimal(qty), new BigDecimal(price))), date, ACTOR);
    }

    // ---- Manufacturing helpers ----------------------------------------------------------------------

    /** WO → release → issue → complete the full quantity. WIP nets back to zero on completion. */
    private void produce(Long finished, Long bomId, String qty, Long stock, LocalDate date) {
        BigDecimal q = new BigDecimal(qty);
        WorkOrder wo = workOrderService.create(finished, bomId, q, date.minusDays(6), date, ACTOR);
        workOrderService.release(wo.getId(), ACTOR);
        workOrderService.issue(wo.getId(), stock, date, ACTOR);
        workOrderService.complete(wo.getId(), q, stock, date, ACTOR);
    }

    /** WO → release only (never issued), so it stays RELEASED and touches no WIP account. */
    private void releaseWorkOrder(Long finished, Long bomId, String qty, LocalDate plannedStart,
                                  LocalDate plannedEnd) {
        WorkOrder wo = workOrderService.create(finished, bomId, new BigDecimal(qty), plannedStart,
                plannedEnd, ACTOR);
        workOrderService.release(wo.getId(), ACTOR);
    }

    // ---- Sales helpers ------------------------------------------------------------------------------

    /** SO → confirm → deliver → invoice (+VAT) → collect in full. Books revenue/COGS, leaves no AR. */
    private void sellAndCollect(Long customer, Long item, String qty, String price, LocalDate date,
                                Long stock) {
        SalesInvoice posted = deliverAndInvoice(customer, item, qty, price, date, stock);
        paymentService.payIn(customer, posted.getGrossAmount(), date,
                List.of(new ReceiptAllocation(posted.getId(), posted.getGrossAmount())), ACTOR);
    }

    /** SO → confirm → deliver → invoice (+VAT), left unpaid. Books revenue/COGS and an open AR balance. */
    private void sellOnCredit(Long customer, Long item, String qty, String price, LocalDate date,
                              Long stock) {
        deliverAndInvoice(customer, item, qty, price, date, stock);
    }

    private SalesInvoice deliverAndInvoice(Long customer, Long item, String qty, String price,
                                           LocalDate date, Long stock) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        SalesOrder so = salesOrderService.createOrder(customer,
                List.of(new SoLineInput(item, q, p)), date, ACTOR);
        salesOrderService.confirm(so.getId(), ACTOR);
        Long line = so.getLines().get(0).getId();
        deliveryService.deliver(so.getId(), stock, List.of(new DeliveryLineInput(line, q)), date, ACTOR);
        return salesInvoiceService.postInvoice(so.getId(), List.of(new InvoiceLineInput(line, q, p)),
                TAX_CODE, date, ACTOR);
    }

    private SalesOrder draftSo(Long customer, Long item, String qty, String price, LocalDate date) {
        return salesOrderService.createOrder(customer,
                List.of(new SoLineInput(item, new BigDecimal(qty), new BigDecimal(price))), date, ACTOR);
    }

    /** The i-th spread date, clamped to the last available month (today) when there are fewer months. */
    private static LocalDate monthAt(List<LocalDate> months, int i) {
        return months.get(Math.min(i, months.size() - 1));
    }
}
