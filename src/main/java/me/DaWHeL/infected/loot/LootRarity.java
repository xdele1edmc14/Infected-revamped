package me.DaWHeL.infected.loot;

public enum LootRarity {
    COMMON(100),
    UNCOMMON(50),
    RARE(20),
    EPIC(8),
    LEGENDARY(2);

    private final int weight;

    LootRarity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public LootRarity next() {
        LootRarity[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public LootRarity previous() {
        LootRarity[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }
}
