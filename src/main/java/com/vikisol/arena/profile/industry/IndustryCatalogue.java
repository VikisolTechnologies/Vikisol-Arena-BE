package com.vikisol.arena.profile.industry;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.profile.entity.Industry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * FE-API-GAPS row 62: the open, staff-managed industry list (arena_industries, V44). Replaces
 * Industry.fromWireValue: a value is matched by label or key, case-insensitively. Industries are
 * retired by deactivating them; a retired one stays valid for the profiles, companies and jobs
 * that already use it, but can't be newly picked.
 */
@Service
@RequiredArgsConstructor
public class IndustryCatalogue {

    public record IndustryRow(String key, String label, boolean active, int position) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        Map<String, String> labels = new LinkedHashMap<>();
        all().forEach(r -> labels.put(r.key(), r.label()));
        Industry.refresh(labels);
    }

    @Transactional(readOnly = true)
    public List<IndustryRow> all() {
        return jdbc.query("select key, label, active, position from arena_industries order by position, label",
                (rs, i) -> new IndustryRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)));
    }

    @Transactional(readOnly = true)
    public List<IndustryRow> active() {
        return all().stream().filter(IndustryRow::active).toList();
    }

    /** For filters: any known industry, active or retired. */
    @Transactional(readOnly = true)
    public Industry resolve(String value) {
        return Industry.of(find(value).key());
    }

    /** For saving a profile, company or job: must be active, unless it's the value already saved. */
    @Transactional(readOnly = true)
    public Industry resolveForWrite(String value, Industry current) {
        IndustryRow row = find(value);
        Industry industry = Industry.of(row.key());
        if (!row.active() && !industry.equals(current)) {
            throw new BadRequestException("industry: " + row.label() + " is no longer offered");
        }
        return industry;
    }

    private IndustryRow find(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("industry: is required");
        String v = value.trim();
        return jdbc.query("select key, label, active, position from arena_industries where lower(label) = lower(?) or key = upper(?)",
                        (rs, i) -> new IndustryRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)), v, v)
                .stream().findFirst()
                .orElseThrow(() -> new BadRequestException("industry: unknown value " + v));
    }

    @Transactional
    public IndustryRow add(UUID adminId, String label, Integer position) {
        String clean = cleanLabel(label);
        String key = clean.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (key.isEmpty()) throw new BadRequestException("label: must contain a letter or digit");
        if (key.length() > 64) key = key.substring(0, 64);
        Integer clash = jdbc.queryForObject("select count(*) from arena_industries where key = ? or lower(label) = lower(?)",
                Integer.class, key, clean);
        if (clash != null && clash > 0) throw new BadRequestException("label: that industry already exists");
        int pos = position != null ? position
                : jdbc.queryForObject("select coalesce(max(position), 0) + 10 from arena_industries", Integer.class);
        jdbc.update("insert into arena_industries (key, label, active, position) values (?, ?, true, ?)", key, clean, pos);
        auditService.record(null, adminId, AuditActions.INDUSTRY_ADDED, "industry:" + key, clean);
        reloadAfterCommit();
        return new IndustryRow(key, clean, true, pos);
    }

    @Transactional
    public IndustryRow update(UUID adminId, String key, String label, Boolean active, Integer position) {
        IndustryRow row = jdbc.query("select key, label, active, position from arena_industries where key = ?",
                        (rs, i) -> new IndustryRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)), key)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Industry not found: " + key));
        String newLabel = label == null ? row.label() : cleanLabel(label);
        if (!newLabel.equalsIgnoreCase(row.label())) {
            Integer clash = jdbc.queryForObject("select count(*) from arena_industries where key <> ? and lower(label) = lower(?)",
                    Integer.class, key, newLabel);
            if (clash != null && clash > 0) throw new BadRequestException("label: that industry already exists");
        }
        boolean newActive = active == null ? row.active() : active;
        int newPosition = position == null ? row.position() : position;
        jdbc.update("update arena_industries set label = ?, active = ?, position = ?, updated_at = now() where key = ?",
                newLabel, newActive, newPosition, key);
        auditService.record(null, adminId, AuditActions.INDUSTRY_UPDATED, "industry:" + key,
                "label=" + newLabel + ", active=" + newActive + ", position=" + newPosition);
        reloadAfterCommit();
        return new IndustryRow(key, newLabel, newActive, newPosition);
    }

    private static String cleanLabel(String label) {
        if (label == null || label.isBlank()) throw new BadRequestException("label: is required");
        String clean = label.trim().replaceAll("\\s+", " ");
        if (clean.length() > 80) throw new BadRequestException("label: must be at most 80 characters");
        return clean;
    }

    // The label cache is process-wide, so it's reloaded from the table once the transaction ends
    // (committed or rolled back), never from an uncommitted change.
    private void reloadAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    load();
                }
            });
        } else {
            load();
        }
    }
}
