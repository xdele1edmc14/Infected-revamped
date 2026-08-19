package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static me.DaWHeL.infected.CombatPolicy.Decision;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatPolicyTest {
    private final CombatPolicy policy = new CombatPolicy();

    @Test
    void cancelsFriendlyFireForBothTeams() {
        assertAll(
                () -> assertEquals(new Decision(true, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.SURVIVOR,
                        ParticipantRole.SURVIVOR,
                        false,
                        false)),
                () -> assertEquals(new Decision(true, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.INFECTED,
                        ParticipantRole.INFECTED,
                        true,
                        false)),
                () -> assertEquals(new Decision(true, false), policy.decide(
                        RoundPhase.LOBBY,
                        ParticipantRole.SURVIVOR,
                        ParticipantRole.SURVIVOR,
                        true,
                        false))
        );
    }

    @Test
    void blocksEveryInfectedAttackBeforeActivePlay() {
        for (RoundPhase phase : new RoundPhase[]{
                RoundPhase.LOBBY, RoundPhase.COUNTDOWN, RoundPhase.HEADSTART, RoundPhase.ENDING}) {
            assertEquals(new Decision(true, false), policy.decide(
                    phase,
                    ParticipantRole.INFECTED,
                    ParticipantRole.SURVIVOR,
                    true,
                    false));
        }
    }

    @Test
    void keepsInfectedCombatDirectMeleeOnly() {
        assertAll(
                () -> assertEquals(new Decision(true, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.INFECTED,
                        ParticipantRole.SURVIVOR,
                        false,
                        false)),
                () -> assertEquals(new Decision(false, true), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.INFECTED,
                        ParticipantRole.SURVIVOR,
                        true,
                        false)),
                () -> assertEquals(new Decision(false, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.INFECTED,
                        ParticipantRole.SURVIVOR,
                        true,
                        true))
        );
    }

    @Test
    void doesNotInventRulesForNonParticipants() {
        assertAll(
                () -> assertEquals(new Decision(false, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.NONE,
                        ParticipantRole.SURVIVOR,
                        true,
                        false)),
                () -> assertEquals(new Decision(false, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.SURVIVOR,
                        ParticipantRole.INFECTED,
                        false,
                        false)),
                () -> assertEquals(new Decision(false, false), policy.decide(
                        RoundPhase.ACTIVE,
                        ParticipantRole.INFECTED,
                        ParticipantRole.NONE,
                        true,
                        false))
        );
    }
}
