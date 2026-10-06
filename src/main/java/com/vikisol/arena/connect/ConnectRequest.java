package com.vikisol.arena.connect;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.jobs.entity.JobPosting;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Flow §8 Talent search / FE-API-GAPS row 34: an employer reaching out asks the person first. One
// per company and person.
@Entity
@Table(name = "arena_connect_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectRequest extends BaseEntity {

    public enum Status { PENDING, ACCEPTED, DECLINED }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private EnterpriseProfile tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private JobPosting job;

    @Column(nullable = false, length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.PENDING;

    private Instant decidedAt;
}
