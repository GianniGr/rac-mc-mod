package com.github.giannigr.racmc.clank;

/** Remap-independent semantic samples. No client action/position/velocity authority. */
public record Rac1ClankInput(boolean jumpA, boolean jumpB, boolean crouch,
                             double left, double forward, double yawDegrees) {
    public static final Rac1ClankInput RELEASED = new Rac1ClankInput(false, false, false, 0, 0, 0);
    public Rac1ClankInput {
        if (!Double.isFinite(left) || !Double.isFinite(forward) || !Double.isFinite(yawDegrees)
                || Math.abs(left) > 1 || Math.abs(forward) > 1 || Math.abs(yawDegrees) > 360_000)
            throw new IllegalArgumentException("Invalid semantic Clank input");
        double length = Math.hypot(left, forward);
        if (length > 1) { left /= length; forward /= length; }
        yawDegrees = Math.IEEEremainder(yawDegrees, 360);
    }
    public boolean jumpHeld() { return jumpA || jumpB; }
    /** Native Input booleans already follow the player's configurable key mappings. */
    public static Rac1ClankInput digital(boolean jumpA, boolean jumpB, boolean crouch,
            boolean left, boolean right, boolean forward, boolean back, double yaw) {
        return new Rac1ClankInput(jumpA, jumpB, crouch,
                (left ? 1 : 0) - (right ? 1 : 0),
                (forward ? 1 : 0) - (back ? 1 : 0), yaw);
    }
    public double worldX() {
        double yaw = Math.toRadians(yawDegrees);
        return left * Math.cos(yaw) - forward * Math.sin(yaw);
    }
    public double worldZ() {
        double yaw = Math.toRadians(yawDegrees);
        return left * Math.sin(yaw) + forward * Math.cos(yaw);
    }
    public double magnitude() { return Math.hypot(left, forward); }

    /** Connection-owned bounded sampler; one edge is derived by the controller, not trusted. */
    public static final class Stream {
        private final long epoch;
        private long serial = -1, acceptedSerial = -1, receivedTick = -1, rateTick = -1;
        private int receivedThisTick;
        private boolean acceptedHeld,pressed;
        private Rac1ClankInput sample = RELEASED;
        private Consumed consumed=new Consumed(-1,-1,-1,false,RELEASED);
        public Stream(long epoch) {
            if (epoch < 0) throw new IllegalArgumentException("Invalid epoch");
            this.epoch = epoch;
        }
        public boolean offer(long epoch, long serial, long serverTick, Rac1ClankInput input) {
            if (input == null || serverTick < 0 || epoch != this.epoch || serial < 0
                    || serial <= this.serial || serverTick < receivedTick) return false;
            if (rateTick != serverTick) { rateTick = serverTick; receivedThisTick = 0; }
            // Consume invalid/excess serials too: a replay cannot become admissible next tick.
            this.serial = serial;
            if (++receivedThisTick > 2) return false;
            receivedTick = serverTick;
            acceptedSerial = serial;
            if(input.jumpHeld()&&!acceptedHeld)pressed=true;
            acceptedHeld=input.jumpHeld();
            sample = input;
            return true;
        }
        /** Lost/disconnected held keys cannot sustain a controller indefinitely. */
        public Rac1ClankInput at(long serverTick) {
            return receivedTick < 0 || serverTick < receivedTick || serverTick - receivedTick > 4
                    ? RELEASED : sample;
        }
        public long acknowledgedSerial() { return acceptedSerial; }
        public boolean acceptedJumpHeld() { return acceptedHeld; }
        public long receivedTick() { return receivedTick; }
        public record Consumed(long serial,long receiptTick,long controlTick,
                               boolean fresh,Rac1ClankInput frame) {}
        /**
         * Once per authoritative tick. Main-thread network tasks may run before the next
         * world-time increment; a new serial from that boundary belongs to this step.
         * Reusing a sample for motion's four-tick grace never renews damage eligibility.
         */
        public Consumed consume(long tick) {
            if(tick<0||tick<consumed.controlTick())throw new IllegalArgumentException("Invalid consume tick");
            if(tick==consumed.controlTick())return consumed;
            boolean fresh=acceptedSerial>consumed.serial()&&receivedTick>=0
                    &&receivedTick<=tick&&tick-receivedTick<=1;
            consumed=new Consumed(acceptedSerial,receivedTick,tick,fresh,at(tick));
            return consumed;
        }
        public Consumed consumed() { return consumed; }
        /** An unconsumed newer sample cannot supply proof for an already-running step. */
        public boolean heldForStep(long tick) {
            return consumed.controlTick()==tick&&consumed.fresh()&&consumed.frame().jumpHeld()
                    &&consumed.serial()==acceptedSerial&&sample.jumpHeld();
        }
        public boolean takeJumpEdge(long tick) {
            boolean result=pressed&&receivedTick>=0&&tick>=receivedTick&&tick-receivedTick<=4;
            pressed=false;
            return result;
        }
    }
}
