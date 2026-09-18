package com.growdigitalbridge.project.domain;

/**
 * DATABASE.md documents only "code/name/status/owner ref" for Project with no enumerated
 * states. Mirrors the simplest documented lifecycle already used for Department/Team
 * (ACTIVE/INACTIVE) rather than inventing an undocumented multi-state workflow.
 */
public enum ProjectStatus {
    ACTIVE,
    INACTIVE
}
