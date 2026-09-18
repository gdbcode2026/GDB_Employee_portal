package com.growdigitalbridge.document.domain;

/** Only "publish"/"manage" are documented actions (API.md); this is the minimal two-state lifecycle they imply. */
public enum PolicyStatus {
    DRAFT,
    PUBLISHED
}
