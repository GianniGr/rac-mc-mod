package com.github.giannigr.racmc.serverassets;

/** Local operator diagnostics. Never send detail (which can contain paths) to a client. */
public record Rac1WrenchDataStatus(State state, String detail, Origin origin) {
    public enum State { MISSING, VALIDATING, IMPORTING, READY, FAILED, CLOSED }
    public enum Origin { NONE, CACHE, SHARED_CACHE, IMPORT }
}
