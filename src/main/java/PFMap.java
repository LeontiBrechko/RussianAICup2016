import java.util.ArrayDeque;

import static java.lang.Math.max;
import static java.lang.Math.min;

@SuppressWarnings("WeakerAccess")
public abstract class PFMap {
    protected ArrayDeque<PotentialField> fields;

    public PFMap() {
        this.fields = new ArrayDeque<>();
    }

    public void addPotentialField(PotentialField field) {
        fields.offer(field);
    }

    public void removePotentialField(PotentialField field) {
        fields.remove(field);
    }

    public void clearPotentialMap() {
        fields.clear();
    }

    public abstract int getPotential(int x, int y, int maxDetectionRange, int maxShootingRange);

    public static class RepelPFMap extends PFMap {
        @Override
        public int getPotential(int x, int y, int maxDetectionRange, int maxShootingRange) {
            int potential = 0;
            for (PotentialField field : fields)
                potential = min(potential, field.getLocalPotential(x, y, maxDetectionRange, maxShootingRange));
            return potential;
        }
    }

    public static class AttractPFMap extends PFMap {
        @Override
        public int getPotential(int x, int y, int maxDetectionRange, int maxShootingRange) {
            int potential = 0;
            for (PotentialField field : fields)
                potential = max(potential, field.getLocalPotential(x, y, maxDetectionRange, maxShootingRange));
            return potential;
        }
    }
}
