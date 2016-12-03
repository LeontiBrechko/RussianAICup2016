import model.*;

import java.util.ArrayDeque;

@SuppressWarnings("WeakerAccess")
public class PFAgent {
    private static final int TRAIL_LENGTH = 100;
    private static final int TRAIL_POTENTIAL = (int) (Constants.NOT_PASSABLE_POTENTIAL);

    private ArrayDeque<PFMap> dynamicMaps;
    private ArrayDeque<PFAgentTrail> trails;

    public PFAgent() {
        trails = new ArrayDeque<>(TRAIL_LENGTH);
    }

    public Point2D handleNextTick(Wizard self, World world, Game game, Point2D wayPointToGo) {
        populatePFMaps(self, world, game, wayPointToGo);
//        debug(false, self, world);
        return getBestPointToMove(self);
    }

    private void populatePFMaps(Wizard self, World world, Game game, Point2D wayPointToGo) {
        dynamicMaps = new ArrayDeque<>();

        PFMap repelPFMap = new PFMap.RepelPFMap();
        addTreeRepelPFs(self, world, repelPFMap);
        addBuildingRepelPFs(self, world, repelPFMap);
        addMinionRepelPFs(self, world, repelPFMap);
        addWizardRepelPFs(self, world, repelPFMap);
        dynamicMaps.offer(repelPFMap);

        PFMap attractPFMap = new PFMap.AttractPFMap();
        attractPFMap.addPotentialField(new PotentialField.WayPointAttractPF(
                (int) wayPointToGo.getX(), (int) wayPointToGo.getY()));
        dynamicMaps.offer(attractPFMap);
    }

    private Point2D getBestPointToMove(Wizard self) {
        int x = (int) self.getX();
        int y = (int) self.getY();
        if (trails.size() >= TRAIL_LENGTH) trails.remove();
        trails.offer(new PFAgentTrail(x, y, TRAIL_POTENTIAL));

        int maxX = x, maxY = y, nextValue, maxValue = potentialSumInPoint(x, y, self);

        for (int deltaX = -1; deltaX <= 1; deltaX++) {
            for (int deltaY = -1; deltaY <= 1; deltaY++) {
                if (deltaX == 0 && deltaY == 0) continue;
                nextValue = potentialSumInPoint(x + deltaX * Constants.STEP_SIZE,
                        y + deltaY * Constants.STEP_SIZE, self);
                if (nextValue > maxValue) {
                    maxValue = nextValue;
                    maxX = x + deltaX * Constants.STEP_SIZE;
                    maxY = y + deltaY * Constants.STEP_SIZE;
                }
            }
        }

        return new Point2D(maxX, maxY);
    }

    private int potentialSumInPoint(int x, int y, Wizard self) {
        int sum = 0;
        for (PFMap pfMap : dynamicMaps)
            sum += pfMap.getPotential(x, y, (int) self.getVisionRange(), (int) self.getCastRange());
        for (PFAgentTrail trail : trails)
            if (trail.getX() == x && trail.getY() == y) sum += trail.getPotential();
        return sum;
    }

    // Repel
    private void addTreeRepelPFs(Wizard self, World world, PFMap repelPFMap) {
        Tree[] trees = world.getTrees();
        for (Tree tree : trees) {
            if (Utils.isUnitInVisionRange(self, tree)) {
                repelPFMap.addPotentialField(new PotentialField.TreeRepelPF(
                        (int) (tree.getX()), (int) (tree.getY()), (int) tree.getRadius()
                ));
            }
        }
    }

    private void addBuildingRepelPFs(Wizard self, World world, PFMap repelPFMap) {
        Building[] buildings = world.getBuildings();
        for (Building building : buildings) {
            if (Utils.isUnitInVisionRange(self, building)) {
                repelPFMap.addPotentialField(new PotentialField.BuildingPFRepel(
                        (int) (building.getX()), (int) (building.getY()),
                        5, 1.0, 1.0, (int) building.getRadius()
                ));
            }
        }
    }

    private void addMinionRepelPFs(Wizard self, World world, PFMap repelPFMap) {
        Minion[] minions = world.getMinions();
        for (Minion minion : minions) {
            if (Utils.isUnitInVisionRange(self, minion)) {
                repelPFMap.addPotentialField(new PotentialField.MinionPFRepel(
                        (int) (minion.getX()), (int) (minion.getY()), (int) minion.getRadius()
                ));
            }
        }
    }

    private void addWizardRepelPFs(Wizard self, World world, PFMap repelPFMap) {
        Wizard[] wizards = world.getWizards();
        for (Wizard wizard : wizards) {
            if (!wizard.isMe() && Utils.isUnitInVisionRange(self, wizard)) {
                repelPFMap.addPotentialField(new PotentialField.WizardRepelPF(
                        (int) (wizard.getX()), (int) (wizard.getY()), (int) wizard.getRadius()
                ));
            }
        }
    }

    // Attract

    // Inner classes
    private static class PFAgentTrail {
        private int x;
        private int y;
        private int potential;

        public PFAgentTrail(int x, int y, int potential) {
            this.x = x;
            this.y = y;
            this.potential = potential;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getPotential() {
            return potential;
        }
    }

//    @SuppressWarnings("Duplicates")
//    private void debug(boolean flag, Wizard self, World world) {
//        if (flag) {
//            int x0 = max(0, (int) ((self.getX() - Constants.DEBUG_RANGE)) / Constants.STEP_SIZE);
//            int y0 = max(0, (int) ((self.getY() - Constants.DEBUG_RANGE)) / Constants.STEP_SIZE);
//            int x1 = min(4000, (int) ((self.getX() + Constants.DEBUG_RANGE)) / Constants.STEP_SIZE);
//            int y1 = min(4000, (int) ((self.getY() + Constants.DEBUG_RANGE)) / Constants.STEP_SIZE);
//            int[][] pixels = new int[y1 - y0 + 1][x1 - x0 + 1];
//            for (int y = y0, i = 0; y <= y1; y++, i++) {
//                for (int x = x0, j = 0; x <= x1; x++, j++) {
//                    pixels[i][j] = potentialSumInPoint(x * Constants.STEP_SIZE, y * Constants.STEP_SIZE, self);
//                }
//            }
//            try {
//                PFDebugger.debug(pixels, world.getTickIndex());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
