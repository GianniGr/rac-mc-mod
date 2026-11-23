package com.github.giannigr.racmc.clank;

/** Presentation predicates only. No input, grants, motion steps or fall-grace decisions. */
public final class Rac1ClankAv {
    public enum Loop {
        HELI(2,3), THRUSTER(18,4), HYDRO(19,5);
        private final int local,slot;
        Loop(int local,int slot){this.local=local;this.slot=slot;}
        public int ownerClass(){return 0;}
        public int local(){return local;}
        public int slot(){return slot;}
    }
    public record Selection(Loop loop,boolean hydroJets,boolean flames,boolean glide) {
        public static final Selection NONE=new Selection(null,false,false,false);
    }
    private Rac1ClankAv() {}
    public static long presentationEpoch(Rac1ClankSession.Snapshot clank,Rac1HydroWire.Snapshot hydro) {
        return hydro!=null&&hydro.access()&&hydro.player().equals(clank.player())
                &&hydro.clankEpoch()==clank.epoch()?hydro.epoch():clank.epoch();
    }
    public static Selection select(Rac1ClankSession.Snapshot clank,Rac1HydroWire.Snapshot hydro,
                                   boolean valid,double verticalVelocity) {
        if(!valid||clank==null||!clank.equipment().worn()||!Double.isFinite(verticalVelocity))
            return Selection.NONE;
        boolean same=hydro!=null&&hydro.player().equals(clank.player())&&hydro.clankEpoch()==clank.epoch();
        if(same&&hydro.access()&&hydro.wet()) {
            boolean powered=hydro.controlled()&&hydro.water()==Rac1HydroWater.Mode.SUBMERGED
                    &&hydro.motion().stage()==Rac1HydroMotion.Stage.THRUST;
            return powered?new Selection(Loop.HYDRO,true,false,false):Selection.NONE;
        }
        boolean glide=clank.action()==Rac1ClankCapability.Action.GLIDE;
        var module=clank.equipment().selected();
        if(module==Rac1ClankEquipment.Module.HELI)
            return glide?new Selection(Loop.HELI,false,false,true):Selection.NONE;
        if(module!=Rac1ClankEquipment.Module.THRUSTER)return Selection.NONE;
        boolean flame=glide||clank.action()==Rac1ClankCapability.Action.HIGH_JUMP&&verticalVelocity>=0
                ||clank.action()==Rac1ClankCapability.Action.LONG_JUMP&&clank.phaseSourceSteps()<44;
        return new Selection(glide?Loop.THRUSTER:null,false,flame,glide);
    }
}
