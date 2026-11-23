package com.github.giannigr.racmc.clank;

/** Fresh Hydro state owns wet presentation; aerial updates intentionally stop in water. */
public final class Rac1HydroPresentation {
    private Rac1HydroPresentation() {}
    public static boolean fresh(Rac1ClankSession.Snapshot clank,Rac1HydroWire.Snapshot hydro,
                                long receivedLocalTick,long localTick) {
        return clank!=null&&hydro!=null&&clank.equipment().worn()&&hydro.access()&&hydro.wet()
                &&hydro.player().equals(clank.player())&&hydro.clankEpoch()==clank.epoch()
                &&receivedLocalTick>=0&&localTick>=receivedLocalTick&&localTick-receivedLocalTick<=4;
    }
}
