package net.sixik.sam;

public final class SamConfig {
    public static final SamConfig DEFAULT = builder().build();

    private final int speed;
    private final int pitch;
    private final int mouth;
    private final int throat;
    private final boolean singMode;
    private final boolean debug;

    private SamConfig(Builder builder) {
        this.speed = builder.speed;
        this.pitch = builder.pitch;
        this.mouth = builder.mouth;
        this.throat = builder.throat;
        this.singMode = builder.singMode;
        this.debug = builder.debug;
    }

    public int speed() { return speed; }
    public int pitch() { return pitch; }
    public int mouth() { return mouth; }
    public int throat() { return throat; }
    public boolean singMode() { return singMode; }
    public boolean debug() { return debug; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int speed = 72;
        private int pitch = 64;
        private int mouth = 128;
        private int throat = 128;
        private boolean singMode;
        private boolean debug;

        public Builder speed(int speed) { this.speed = speed & 0xFF; return this; }
        public Builder pitch(int pitch) { this.pitch = pitch & 0xFF; return this; }
        public Builder mouth(int mouth) { this.mouth = mouth & 0xFF; return this; }
        public Builder throat(int throat) { this.throat = throat & 0xFF; return this; }
        public Builder singMode(boolean singMode) { this.singMode = singMode; return this; }
        public Builder debug(boolean debug) { this.debug = debug; return this; }
        public SamConfig build() { return new SamConfig(this); }
    }
}
