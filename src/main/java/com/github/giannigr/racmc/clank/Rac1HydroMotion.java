package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;

/** Independent source60 submerged propulsion, not vanilla surface swimming or a new acquisition policy. */
public final class Rac1HydroMotion {
    public static final double DT=1.0/60;
    public enum Stage { NATIVE, THRUST, DRIFT }
    public record Input(boolean thrust,boolean rise,boolean dive,double left,double forward,double yaw) {
        public static final Input RELEASED=new Input(false,false,false,0,0,0);
        public Input {
            var normalized=new Rac1ClankInput(rise,false,dive,left,forward,yaw);
            left=normalized.left();forward=normalized.forward();yaw=normalized.yawDegrees();
        }
        public double magnitude(){return Math.hypot(left,forward);}
    }
    public record State(Stage stage,int phase,int stroke,int diveIn,double speed,double yaw,double yawRate,
                        double pitch,double pitchRate,Vector effective) {
        public State {
            if(stage==null||phase<0||phase>36000||stroke<0||stroke>36000||diveIn< -1||diveIn>41
                    ||!Double.isFinite(speed)||speed<0||speed>32||!Double.isFinite(yaw)||Math.abs(yaw)>Math.PI+.001
                    ||!Double.isFinite(yawRate)||Math.abs(yawRate)>Math.toRadians(5)+.001
                    ||!Double.isFinite(pitch)||Math.abs(pitch)>Math.toRadians(78)+.001
                    ||!Double.isFinite(pitchRate)||Math.abs(pitchRate)>Math.toRadians(70)/60+.001
                    ||effective==null||effective.length()>32)throw new IllegalArgumentException("Invalid Hydro state");
        }
    }
    public record Step(boolean controlled,Vector displacement) {}
    private Stage stage=Stage.NATIVE;
    private int phase,stroke,diveIn=-1;
    private double speed,yaw,yawRate,pitch,pitchRate;
    private Vector effective=Vector.ZERO;
    private boolean unresolved;
    public Step step(Input input,Vector nativeVelocity,boolean enteredByDive,boolean lowAir) {
        return step(input,nativeVelocity,enteredByDive,lowAir,0);
    }
    /** Native Minecraft look pitch is positive down. It only targets pitch during held thrust. */
    public Step step(Input input,Vector nativeVelocity,boolean enteredByDive,boolean lowAir,double nativeLookPitch) {
        if(unresolved)throw new IllegalStateException("Unresolved Hydro collision");
        if(stage==Stage.NATIVE) {
            if(input.magnitude()>.15&&nativeVelocity.length()>.1)stroke=Math.min(36000,stroke+1);
            else stroke=0;
            if(!input.thrust()||!enteredByDive&&stroke>0&&stroke<=20)return new Step(false,Vector.ZERO);
            stage=Stage.THRUST;phase=0;speed=Math.min(32,nativeVelocity.length());
            yaw=Math.toRadians(input.yaw());yawRate=0;
            if(enteredByDive)diveIn=0;
        } else if(stage==Stage.THRUST&&!input.thrust()) {
            if(input.magnitude()>.5){cancel();return new Step(false,Vector.ZERO);}
            if(!input.rise()&&!input.dive()&&input.magnitude()<.7&&(diveIn<0||diveIn>40)) {
                stage=Stage.DRIFT;phase=0;speed=effective.length();
            }
        } else if(stage==Stage.DRIFT) {
            if(input.thrust()){stage=Stage.THRUST;phase=0;}
            else if(input.magnitude()>.5||input.rise()||input.dive()){cancel();return new Step(false,Vector.ZERO);}
        }
        phase=Math.min(36000,phase+1);
        if(stage==Stage.DRIFT) {
            double previous=effective.length();speed=Math.max(0,previous-8*DT);
            if(speed<.02){cancel();return new Step(false,Vector.ZERO);}
            effective=effective.scale(speed/Math.max(previous,1e-9));
        } else {
            boolean diving=diveIn>=0&&diveIn<=40;
            if(diveIn>=0)diveIn=Math.min(41,diveIn+1);
            double targetYaw=input.magnitude()>.001?Math.toRadians(input.yaw())
                    +Math.atan2(-input.left(),input.forward()):yaw;
            yawRate=Math.clamp(yawRate+wrap(targetYaw-yaw)*.007-yawRate*.08,
                    -Math.toRadians(300)*DT,Math.toRadians(300)*DT);
            yaw=wrap(yaw+yawRate);
            double targetPitch=input.rise()||lowAir?-Math.toRadians(78):
                    input.dive()?Math.toRadians(78):input.thrust()?Math.toRadians(boundedLookPitch(nativeLookPitch)):0;
            if(diving)targetPitch=Math.max(Math.toRadians(78)*.35,targetPitch);
            pitchRate=Math.clamp(pitchRate+(targetPitch-pitch)*.015-pitchRate*.2,
                    -Math.toRadians(70)*DT,Math.toRadians(70)*DT);
            pitch=Math.clamp(pitch+pitchRate,-Math.toRadians(78),Math.toRadians(78));
            double factor=Math.max(input.magnitude(),input.rise()||input.dive()||diving?1:0);
            if(factor>.15)factor=Math.max(.4,factor);
            double target=diving?6:input.thrust()?7:2.8*factor;
            speed+=Math.clamp(target-speed,-8*DT,13*DT);
            effective=new Vector(-Math.sin(yaw)*Math.cos(pitch)*speed,-Math.sin(pitch)*speed,
                    Math.cos(yaw)*Math.cos(pitch)*speed);
        }
        unresolved=true;
        return new Step(true,effective.scale(DT));
    }
    public void resolved(Vector actual) {
        if(!unresolved)throw new IllegalStateException("No Hydro collision pending");
        unresolved=false;effective=actual.scale(60);
        if(effective.length()>32)effective=effective.scale(32/effective.length());
        speed=Math.min(speed,effective.length());
    }
    public void cancel(){stage=Stage.NATIVE;phase=0;stroke=0;diveIn=-1;unresolved=false;}
    public boolean controlled(){return stage!=Stage.NATIVE;}
    public Vector velocity(){return effective;}
    public void nativeVelocity(Vector value) {
        if(stage!=Stage.NATIVE||unresolved||value.length()>32)throw new IllegalStateException("Not native Hydro handoff");
        effective=value;speed=value.length();
    }
    public State state() {
        if(unresolved)throw new IllegalStateException("Pending Hydro collision");
        return new State(stage,phase,stroke,diveIn,speed,yaw,yawRate,pitch,pitchRate,effective);
    }
    public void restore(State s) {
        stage=s.stage();phase=s.phase();stroke=s.stroke();diveIn=s.diveIn();speed=s.speed();
        yaw=s.yaw();yawRate=s.yawRate();pitch=s.pitch();pitchRate=s.pitchRate();effective=s.effective();unresolved=false;
    }
    public Rac1HydroMotion copy(){var copy=new Rac1HydroMotion();copy.restore(state());return copy;}
    public static double boundedLookPitch(double degrees) {
        return Double.isFinite(degrees)?Math.clamp(degrees,-78,78):0;
    }
    private static double wrap(double value){return Math.IEEEremainder(value,Math.PI*2);}
}
