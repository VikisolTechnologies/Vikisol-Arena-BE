package com.vikisol.arena.platform.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// PA6 (feature flags/demo tools). Deliberately schemaless beyond on/off - a flag is just a
// named switch platform_admin can flip; what each key actually gates lives in whatever code
// reads it, not here.
@Entity
@Table(name = "arena_feature_flags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FeatureFlag extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String label;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;
}
