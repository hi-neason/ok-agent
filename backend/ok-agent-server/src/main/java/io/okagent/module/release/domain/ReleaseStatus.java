package io.okagent.module.release.domain;

/** Lifecycle of a deployment record. At most one PROMOTED release exists per target. */
public enum ReleaseStatus {
    /** Currently serving production traffic for its target. */
    PROMOTED,
    /** Replaced by a newer release on the same target. */
    SUPERSEDED,
    /** Reverted via rollback; the target now points elsewhere. */
    ROLLED_BACK
}
