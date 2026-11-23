package com.github.giannigr.racmc.client.rescue;

/** All viewers draw the same occupant; personal Gear state is not consulted. */
final class Rac1PodViewer {
    private Rac1PodViewer() {}

    static boolean showOccupant(boolean naturalSite, boolean claimed) {
        return naturalSite && !claimed;
    }
}
