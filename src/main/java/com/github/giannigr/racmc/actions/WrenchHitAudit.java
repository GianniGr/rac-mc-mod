package com.github.giannigr.racmc.actions;

/** Per-action bounded local diagnostic counters; no target names, assets or client telemetry. */
final class WrenchHitAudit {
    int samples, candidates, dedup, eligibility, protection, rejected, hits;
    @Override public String toString() {
        return "samples=" + samples + " candidates=" + candidates + " hits=" + hits
                + " dedup=" + dedup + " range/LOS/PvP=" + eligibility
                + " protection=" + protection + " nativeHurtRejected=" + rejected;
    }
}
