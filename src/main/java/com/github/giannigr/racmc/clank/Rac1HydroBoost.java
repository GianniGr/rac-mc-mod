package com.github.giannigr.racmc.clank;

/** Transient Hydro-only latch, driven by fresh physical samples on both sides. */
public enum Rac1HydroBoost {
    BLOCKED, READY, ARMED_DOWN, ARMED_UP;

    public record Controls(boolean active,boolean sprintDown,boolean rise,boolean dive,
                           double left,double forward,double yaw) {
        public static final Controls INACTIVE=new Controls(false,false,false,false,0,0,0);
        public Controls {
            var normalized=new Rac1ClankInput(rise,false,dive,left,forward,yaw);
            left=normalized.left();forward=normalized.forward();yaw=normalized.yawDegrees();
        }
    }

    public boolean armed(){return this==ARMED_DOWN||this==ARMED_UP;}

    public Rac1HydroBoost sample(Controls controls,boolean eligible) {
        if(!eligible||!controls.active())return BLOCKED;
        return switch(this) {
            case BLOCKED -> controls.sprintDown()?BLOCKED:READY;
            case READY -> controls.sprintDown()?ARMED_DOWN:READY;
            case ARMED_DOWN -> controls.sprintDown()?ARMED_DOWN:ARMED_UP;
            case ARMED_UP -> controls.sprintDown()?BLOCKED:ARMED_UP;
        };
    }

    public Rac1HydroMotion.Input drive(Controls controls) {
        if(!armed()||!controls.active())return Rac1HydroMotion.Input.RELEASED;
        return new Rac1HydroMotion.Input(controls.forward()>.15,true,controls.rise(),controls.dive(),
                controls.left(),controls.forward(),controls.yaw());
    }
}
