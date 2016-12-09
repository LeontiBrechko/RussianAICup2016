import model.*;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import static java.lang.Math.abs;

@SuppressWarnings("WeakerAccess")
public class Utils {
    public static boolean canUseFrostBall(Wizard self, Game game, Set<SkillType> skills) {
        return skills.contains(SkillType.FROST_BOLT) &&
                self.getRemainingActionCooldownTicks() <= 0 &&
                self.getRemainingCooldownTicksByAction()[ActionType.FROST_BOLT.ordinal()] <= 0 &&
                self.getMana() >= game.getFrostBoltManacost();
    }

    public static boolean canUseFireBall(Wizard self, Game game, Set<SkillType> skills) {
        return skills.contains(SkillType.FIREBALL) &&
                self.getRemainingActionCooldownTicks() <= 0 &&
                self.getRemainingCooldownTicksByAction()[ActionType.FIREBALL.ordinal()] <= 0 &&
                self.getMana() >= game.getFireballManacost();
    }

    public static Optional<LivingUnit> getFireBallTarget(Wizard self, World world) {
        ArrayDeque<LivingUnit> enemyUnits = new ArrayDeque<>();
        PriorityQueue<UnitNearbyCountPair> pairs = new PriorityQueue<>();
        for (Wizard wizard : world.getWizards())
            if (!wizard.isMe() && wizard.getFaction() != self.getFaction())
                enemyUnits.offer(wizard);
        for (Minion minion : world.getMinions())
            if (minion.getFaction() != self.getFaction() && minion.getFaction() != Faction.NEUTRAL)
                enemyUnits.offer(minion);
        for (Building building : world.getBuildings())
            if (building.getFaction() != self.getFaction())
                enemyUnits.offer(building);

        int count;
        for (LivingUnit enemy : enemyUnits) {
            if (isUnitInCastRange(self, enemy)) {
                count = 0;
                for (LivingUnit nearbyEnemy : enemyUnits) {
                    if (nearbyEnemy != enemy &&
                            enemy.getDistanceTo(nearbyEnemy) - nearbyEnemy.getRadius() <= 100)
                        count++;
                }
                pairs.offer(new UnitNearbyCountPair(enemy, count));
            }
        }

        UnitNearbyCountPair bestPair = pairs.poll();
        while (bestPair != null && self.getDistanceTo(bestPair.unit) - bestPair.unit.getRadius() <= 100)
            bestPair = pairs.poll();

        return Optional.ofNullable(bestPair != null && bestPair.count > 0 ? bestPair.unit : null);
    }

    public static boolean isTowerInMyLane(WayPoints wayPoints, Building tower) {
        if (tower == null || tower.getType() != BuildingType.GUARDIAN_TOWER) return false;
        for (Point2D wayPoint : wayPoints.getCurrentLaneWayPoints()) {
            if (wayPoint.getDistanceTo(tower) <= Constants.WAY_POINT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    public static Optional<LivingUnit> nextBuildingTarget(Building building, World world, Game game) {
        PriorityQueue<LivingUnit> moreThanDamageUnits =
                new PriorityQueue<>(((o1, o2) -> Integer.compare(o1.getLife(), o2.getLife())));
        PriorityQueue<LivingUnit> lessThanDamageUnits =
                new PriorityQueue<>(((o1, o2) -> Integer.compare(o2.getLife(), o1.getLife())));
        for (Minion minion : world.getMinions()) {
            if (minion.getFaction() != building.getFaction() &&
                    building.getDistanceTo(minion) - minion.getRadius() <= game.getGuardianTowerAttackRange()) {
                if (minion.getLife() >= game.getGuardianTowerDamage()) moreThanDamageUnits.offer(minion);
                else lessThanDamageUnits.offer(minion);
            }
        }
        for (Wizard wizard : world.getWizards()) {
            if (wizard.getFaction() != building.getFaction() &&
                    building.getDistanceTo(wizard) - wizard.getRadius() <= game.getGuardianTowerAttackRange()) {
                if (wizard.getLife() >= game.getGuardianTowerDamage()) moreThanDamageUnits.offer(wizard);
                else lessThanDamageUnits.offer(wizard);
            }
        }
        if (moreThanDamageUnits.size() > 0) return Optional.of(moreThanDamageUnits.peek());
        else return Optional.ofNullable(lessThanDamageUnits.peek());
    }

    public static boolean isUnitInVisionRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() <= self.getVisionRange();
    }

    public static boolean isUnitInStaffSector(Wizard self, LivingUnit unit, Game game) {
        return unit != null && abs(self.getAngleTo(unit)) <= game.getStaffSector() / 2.0;
    }

    public static boolean isUnitInCastRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - Constants.RANGE_ERROR <= self.getCastRange();
    }

    public static boolean isUnitInStaffRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - Constants.RANGE_ERROR <= 70.0;
    }

    public static boolean isUnitInCollisionRange(Wizard self, LivingUnit unit) {
        return unit != null && self.getDistanceTo(unit) - unit.getRadius() - self.getRadius()
                <= Constants.COLLISION_BORDER_LENGTH + Constants.STEP_SIZE + 1;
    }

    private static class UnitNearbyCountPair implements Comparable<UnitNearbyCountPair> {
        LivingUnit unit;
        int count;

        public UnitNearbyCountPair(LivingUnit unit, int count) {
            this.unit = unit;
            this.count = count;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public int compareTo(UnitNearbyCountPair o) {
            int res = Integer.compare(this.count, o.count);
            if (res == 0) {
                if ((this.unit instanceof Wizard && o.unit instanceof Wizard) ||
                        (this.unit instanceof Building && o.unit instanceof Building) ||
                        (this.unit instanceof Minion && o.unit instanceof Minion))
                    res = Integer.compare(this.unit.getLife(), o.unit.getLife());
                else {
                    if (this.unit instanceof Wizard) res = -1;
                    else if (o.unit instanceof Wizard) res = 1;
                    else if (this.unit instanceof Building) res = -1;
                    else res = 1;
                }
            }
            return res;
        }
    }
}
