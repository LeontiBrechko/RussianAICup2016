import static java.lang.Math.hypot;

@SuppressWarnings("WeakerAccess")
public abstract class PotentialField {
    protected int x;
    protected int y;

    protected int potential;
    protected double gradation;
    protected double potentialWeight;
    protected int objectRadius;

    public PotentialField(int x, int y, int potential, double gradation, double potentialWeight, int objectRadius) {
        this.x = x;
        this.y = y;
        this.potential = potential;
        this.gradation = gradation;
        this.potentialWeight = potentialWeight;
        this.objectRadius = objectRadius;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getPotential() {
        return potential;
    }

    public void setPotential(int potential) {
        this.potential = potential;
    }

    public double getPotentialWeight() {
        return potentialWeight;
    }

    public void setPotentialWeight(double potentialWeight) {
        this.potentialWeight = potentialWeight;
    }

    protected int getBorderRepelPotential(int distance) {
        return (int) -(Constants.COLLISION_RADIUS - gradation * distance);
    }

    public abstract int getLocalPotential(int x, int y, int maxDetectionRange, int maxShootingRange);

    // Subclasses
    // Repel part
    public static abstract class RepelPF extends PotentialField {
        public RepelPF(int x, int y, int potential, double gradation, double potentialWeight, int objectRadius) {
            super(x, y, potential, gradation, potentialWeight, objectRadius);
        }

        @Override
        public int getLocalPotential(int x, int y, int maxDetectionRange, int maxShootingRange) {
            int localX = this.x - x;
            int localY = this.y - y;
            int distance = (int) (hypot(localX, localY)) - objectRadius - Constants.COLLISION_RADIUS;
            int localPotential;
            if (distance <= 0) localPotential = Constants.NOT_PASSABLE_POTENTIAL;
            else if (distance > 0 && distance <= Constants.COLLESION_BORDER_LENGTH)
                localPotential = getBorderRepelPotential(distance);
            else localPotential = 0;
            return (int) (localPotential * potentialWeight);
        }
    }

    public static class TreeRepelPF extends RepelPF {
        public TreeRepelPF(int x, int y, int objectRadius) {
            super(x, y, 5, 1.0, 1.0, objectRadius);
        }
    }

    public static class WizardRepelPF extends RepelPF {
        public WizardRepelPF(int x, int y, int objectRadius) {
            super(x, y, 5, 1.0, 1.0, objectRadius);
        }

    }

    public static class BuildingPFRepel extends RepelPF {
        public BuildingPFRepel(int x, int y, int potential,
                               double gradation, double potentialWeight, int objectRadius) {
            super(x, y, potential, gradation, potentialWeight, objectRadius);
        }
    }

    public static class MinionPFRepel extends RepelPF {
        public MinionPFRepel(int x, int y, int objectRadius) {
            super(x, y, 10, 1.0, 1.0, objectRadius);
        }
    }

    // Attract part
    public static class WayPointAttractPF extends PotentialField {
        public WayPointAttractPF(int x, int y) {
            super(x, y, (int) (Constants.WAY_POINT_RADIUS * 3), 1.0, 1.0, 0);
        }

        @Override
        public int getLocalPotential(int x, int y, int maxDetectionRange, int maxShootingRange) {
            int localX = this.x - x;
            int localY = this.y - y;
            return (int) (potentialWeight * (potential - gradation * hypot(localX, localY)));
        }
    }
}
