package me.DaWHeL.infected;

import java.util.Objects;

public final class CombatPolicy {

    public Decision decide(
            RoundPhase phase,
            ParticipantRole attacker,
            ParticipantRole victim,
            boolean directPlayerMelee,
            boolean alreadyCancelled
    ) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");

        if ((attacker == ParticipantRole.SURVIVOR && victim == ParticipantRole.SURVIVOR)
                || (attacker == ParticipantRole.INFECTED && victim == ParticipantRole.INFECTED)) {
            return Decision.cancel();
        }
        if (attacker == ParticipantRole.INFECTED && phase != RoundPhase.ACTIVE) {
            return Decision.cancel();
        }
        if (attacker == ParticipantRole.INFECTED && !directPlayerMelee) {
            return Decision.cancel();
        }

        boolean infectVictim = !alreadyCancelled
                && phase == RoundPhase.ACTIVE
                && attacker == ParticipantRole.INFECTED
                && victim == ParticipantRole.SURVIVOR
                && directPlayerMelee;
        return new Decision(false, infectVictim);
    }

    public record Decision(boolean cancelDamage, boolean infectVictim) {
        public static Decision cancel() {
            return new Decision(true, false);
        }
    }
}
