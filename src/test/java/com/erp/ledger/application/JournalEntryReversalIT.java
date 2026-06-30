package com.erp.ledger.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.domain.JournalEntry;
import com.erp.ledger.domain.JournalEntryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Journal-entry reversal (Testcontainers, real security chain). The ledger is append-only, so a correction
 * is a NEW mirror entry; both stay POSTED and net to zero. Asserts the happy path + the guards that protect
 * the reconciliation invariant: only manual entries are reversible, no double-reversal, the reversal must
 * land in an OPEN period, and reverse is ACCOUNTANT-gated. Not {@code @Transactional} (the post commits).
 * Uses 2026 period 9 (untouched by other tests).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JournalEntryReversalIT {

    private static final LocalDate SEPT = LocalDate.of(2026, 9, 15);
    private static final LocalDate NO_PERIOD = LocalDate.of(2099, 1, 1);
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired
    private LedgerPostingService postingService;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private JournalEntryQueryService queryService;
    @Autowired
    private MockMvc mvc;

    @Test
    void reversingAManualEntryPostsAnOffsettingMirrorAndLinksBoth() {
        JournalEntry original = postManual();
        JournalEntry reversal = postingService.reverse(original.getEntryNo(), SEPT, null, "tester");

        assertThat(reversal.getEntryNo()).isNotEqualTo(original.getEntryNo());
        assertThat(reversal.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        assertThat(reversal.getReversesEntryId()).isEqualTo(original.getId());

        JournalEntry reloaded = journalEntries.findByEntryNo(original.getEntryNo()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JournalEntryStatus.POSTED); // stays POSTED, not REVERSED
        assertThat(reloaded.getReversedByEntryId()).isEqualTo(reversal.getId());

        // Per-line debit<->credit swap -> each account nets to zero across the two entries.
        JournalEntryDetail detail = queryService.findDetail(reversal.getEntryNo());
        JournalEntryDetail.Line cash = lineFor(detail, "1010");
        JournalEntryDetail.Line capital = lineFor(detail, "3100");
        assertThat(cash.debit()).isEqualByComparingTo("0");
        assertThat(cash.credit()).isEqualByComparingTo("100");
        assertThat(capital.debit()).isEqualByComparingTo("100");
        assertThat(capital.credit()).isEqualByComparingTo("0");
    }

    @Test
    void cannotReverseADocumentSourcedEntry() {
        JournalEntry doc = postingService.post(request("DOC-" + SEQ.incrementAndGet(),
                "SALES_INVOICE", "INV-" + SEQ.incrementAndGet()), "tester");
        assertThatThrownBy(() -> postingService.reverse(doc.getEntryNo(), SEPT, null, "tester"))
                .isInstanceOf(EntryNotReversibleException.class);
    }

    @Test
    void cannotReverseTheSameEntryTwice() {
        JournalEntry original = postManual();
        postingService.reverse(original.getEntryNo(), SEPT, null, "tester");
        assertThatThrownBy(() -> postingService.reverse(original.getEntryNo(), SEPT, null, "tester"))
                .isInstanceOf(EntryNotReversibleException.class);
    }

    @Test
    void reversalMustLandInAnOpenPeriod() {
        JournalEntry original = postManual();
        assertThatThrownBy(() -> postingService.reverse(original.getEntryNo(), NO_PERIOD, null, "tester"))
                .isInstanceOf(PeriodNotOpenException.class);
    }

    @Test
    void reversingAnUnknownEntryFails() {
        assertThatThrownBy(() -> postingService.reverse(999_999_999L, SEPT, null, "tester"))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    void getByEntryNoReturnsLinesAndReversalLink() throws Exception {
        JournalEntry original = postManual();
        JournalEntry reversal = postingService.reverse(original.getEntryNo(), SEPT, null, "tester");
        String token = login("admin", "admin");

        mvc.perform(get("/api/ledger/journal-entries/" + original.getEntryNo())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryNo").value(original.getEntryNo()))
                .andExpect(jsonPath("$.reversedByEntryNo").value(reversal.getEntryNo()))
                .andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void readOnlyGuestCannotReverse() throws Exception {
        JournalEntry original = postManual();
        mvc.perform(post("/api/ledger/journal-entries/" + original.getEntryNo() + "/reverse")
                        .header("Authorization", login("guest", "guest"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private JournalEntry postManual() {
        return postingService.post(request("MANUAL-" + SEQ.incrementAndGet(), null, null), "tester");
    }

    private static JournalEntryRequest request(String memo, String sourceDocType, String sourceDocId) {
        return new JournalEntryRequest(null, SEPT, memo, null, sourceDocType, sourceDocId,
                sourceDocType != null ? "POSTED" : null,
                List.of(new Line("1010", new BigDecimal("100"), null, "cash"),
                        new Line("3100", null, new BigDecimal("100"), "capital")));
    }

    private static JournalEntryDetail.Line lineFor(JournalEntryDetail detail, String accountCode) {
        return detail.lines().stream()
                .filter(l -> accountCode.equals(l.accountCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line for account " + accountCode));
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = ACCESS_TOKEN.matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("no accessToken in login response: " + body);
        }
        return "Bearer " + m.group(1);
    }
}
