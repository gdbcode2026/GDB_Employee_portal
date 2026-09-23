package com.growdigitalbridge.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code type} is freeform text: no asset classification vocabulary is documented anywhere in
 * this repository, so none is invented here. There is no reachable transition to {@link
 * AssetStatus#RETIRED} in this increment - see the V1 migration comment for why.
 */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String tag;

    @Column(nullable = false, length = 120)
    private String type;

    @Column(length = 120)
    private String serial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected Asset() { }

    public Asset(UUID id, String tag, String type, String serial, String actor, Instant now) {
        this.id = id;
        this.tag = tag;
        this.type = type;
        this.serial = serial;
        this.status = AssetStatus.AVAILABLE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markAssigned(String actor, Instant now) {
        this.status = AssetStatus.ASSIGNED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void markAvailable(String actor, Instant now) {
        this.status = AssetStatus.AVAILABLE;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getTag() { return tag; }
    public String getType() { return type; }
    public String getSerial() { return serial; }
    public AssetStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
