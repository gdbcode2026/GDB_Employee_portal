package com.growdigitalbridge.document.domain;

/**
 * A version's own scan outcome (DATABASE.md: "version ... scan status"). No malware-scanning
 * provider is documented or integrated (see docs/ARCHITECTURE_REVIEW.md item 3); CLEAN/
 * QUARANTINED here reflect the checksum-integrity gate actually implemented in
 * DocumentService#complete, not a real virus scan result.
 */
public enum ScanStatus {
    PENDING,
    CLEAN,
    QUARANTINED
}
