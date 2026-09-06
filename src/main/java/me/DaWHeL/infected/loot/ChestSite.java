package me.DaWHeL.infected.loot;

public record ChestSite(int x, int groundY, int z) {
    public int chestY() {
        return groundY + 1;
    }
}
