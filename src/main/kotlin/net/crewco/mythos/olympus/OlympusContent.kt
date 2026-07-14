package net.crewco.mythos.olympus

import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.era.EraDefinition
import net.crewco.mythos.api.era.Objective
import net.crewco.mythos.api.realm.RealmDefinition
import net.crewco.mythos.api.realm.RealmKind
import net.crewco.mythos.api.realm.RealmRules
import org.bukkit.potion.PotionEffectType
import net.crewco.mythos.api.role.ClaimResult
import net.crewco.mythos.api.role.ClaimRule
import net.crewco.mythos.api.role.ClaimRules
import net.crewco.mythos.api.role.Endurance
import net.crewco.mythos.api.role.RoleDefinition
import net.crewco.mythos.api.role.RoleTier
import net.crewco.mythos.api.story.beats
import net.crewco.mythos.api.story.line
import net.crewco.mythos.api.story.pause
import net.crewco.mythos.api.story.title

/**
 * The age when the world stops being *made* and starts being *governed* — and, far more
 * importantly, the age when there is finally somebody to govern.
 */
class OlympusContent(private val mythos: Mythos) {

    /**
     * Aphrodite is a callback to an addon that isn't this one.
     *
     * She rose from the foam where the blood of Uranus fell into the sea — which happened
     * in EraOfCreation, three chapters and one whole jar ago. If that jar isn't installed,
     * the sea was never cut, and she simply isn't there to be claimed. Nothing crashes.
     * A story that wasn't told leaves a hole exactly the shape of itself.
     */
    private val fromTheFoam = ClaimRule { _, ctx ->
        when {
            mythos.eras.era("chaos") == null ->
                ClaimResult.Deny("There was no sky to cut, on this world. You were never born.")
            "chaos" !in ctx.pastEras ->
                ClaimResult.Deny("The sea has not been cut yet. Wait for the sickle.")
            else -> ClaimResult.Allow
        }
    }

    /**
     * **Olympus is a world, and mortals cannot stand on it.**
     *
     * Not a build with a warning sign — a generated sky-island in an empty world, with an
     * access rule the engine enforces. A mortal who finds a way up is put back down by the
     * world itself, immediately. That's the only way "the gods live somewhere you don't" is
     * ever true on a server where anyone can build a dirt tower.
     */
    val OLYMPUS = RealmDefinition(
        id = "olympus",
        displayName = "Olympus",
        kind = RealmKind.SKY,
        access = RealmRules.DIVINE,
        refusal = "<gray>You are put back down, gently, and without discussion. <dark_gray><i>You are not one of them.",
        entryLore = listOf(
            "<gold><i>Above the weather. Above the argument.",
            "<dark_gray><i>From up here everything below looks small enough to be simple.",
        ),
        flight = true,
        ambientSound = "minecraft:block.beacon.ambient",
        ambientParticle = "END_ROD",
        platformY = 200,
        platformRadius = 32,
        platformMaterial = "QUARTZ_BLOCK",
    )

    /**
     * **The Underworld.** Where the dead go, where Hades is strongest, and where — four addons
     * from now — Heracles will have to ask permission to walk.
     */
    val UNDERWORLD = RealmDefinition(
        id = "underworld",
        displayName = "The House of Hades",
        kind = RealmKind.NETHER,
        access = RealmRules.any(
            RealmRules.roles("hades", "persephone", "charon", "orpheus"),
            RealmRules.SPIRITS, // the dead are already here
            RealmRules.DIVINE,
            // And whoever the king of the dead has personally given leave to — which is how a
            // demigod with a lead and no permission problem gets in to fetch a dog.
            RealmRules.flagged("chthonic.permitted"),
        ),
        refusal = "<dark_gray><i>The living do not come down here. Not without asking.",
        entryLore = listOf(
            "<dark_gray><i>It is not fire. It is a great grey plain, and it goes on.",
            "<gray><i>He drew the worst lot, and rules it better than either of them rules theirs.",
        ),
        ambient = listOf(PotionEffectType.DARKNESS),
        ambientSound = "minecraft:ambient.soul_sand_valley.mood",
        ambientParticle = "SOUL",
    )

    val era = EraDefinition(
        id = ERA,
        displayName = "The Olympian Order",
        order = 2,
        next = "theft-of-fire",
        subtitle = "somebody has to be in charge, and it's going to be him",
        lore = listOf(
            "The war is over. The world is intact, more or less, and nobody is left to argue.",
            "So they do the thing that always happens next: they divide it.",
        ),
        prologue = beats {
            pause(20)
            title("<yellow>The Olympian Order", "<gray>somebody has to be in charge", sound = "minecraft:block.beacon.activate")
            pause(50)
            line("<gray>Three brothers stand in the wreckage of a war they only just won.", delayTicks = 50)
            line("<gray>There is a whole world in front of them and nobody left to say no.", delayTicks = 55)
            pause(40)
            line("<white>They draw lots for it. <gold>/power lots", delayTicks = 45)
            line("<dark_gray><i>Zeus gets the sky. Poseidon gets the sea. Hades draws the dead,", delayTicks = 50)
            line("<dark_gray><i>and rules it better than either of them rules theirs.", delayTicks = 45)
            pause(50)
            line("<gray>And in the mud, far below, something small stands up and looks at the sky.", delayTicks = 60)
            line("<white>Mortals walk the world. <gold>/claim mortal <dark_gray>· <gray>they can pray, and they will.", delayTicks = 40)
        },
        epilogue = beats {
            pause(30)
            title("<gold>Fire", "<gray>and somebody is going to pay for it", delayTicks = 20, sound = "minecraft:item.firecharge.use")
            pause(60)
            line("<gray>The gods have everything, and the mortals have nothing, and one Titan notices.", delayTicks = 55)
            line("<dark_gray><i>He was on the winning side of the war. Zeus trusts him.", delayTicks = 50)
            line("<dark_gray><i>That is going to turn out to be a mistake.", delayTicks = 55)
            pause(60)
        },
        objectives = listOf(
            Objective("the_lots", "The world is divided by lot: sky, sea, and the dead"),
            Objective("olympus_raised", "Olympus is raised above the clouds"),
            Objective("the_twelve", "Twelve thrones are filled"),
            Objective("first_prayer", "A mortal asks a god for something"),
            Objective("aphrodite_rises", "Something beautiful comes out of the water", hidden = true, optional = true),
            Objective("the_titans_bound", "The Titans who fought are put where they cannot reach the sky", optional = true),
        ),
    )

    // ---- the newcomers -------------------------------------------------------

    private fun olympian(
        id: String,
        name: String,
        domains: List<String>,
        lore: String,
        powers: List<String>,
        essence: Int = 40,
        extraRules: List<ClaimRule> = emptyList(),
    ) = RoleDefinition(
        id = id,
        displayName = name,
        tier = RoleTier.OLYMPIAN,
        era = ERA,
        domains = domains,
        color = "<yellow>",
        lore = listOf(lore),
        powers = powers + "boon", // every god can answer a prayer
        claimRules = listOf(
            ClaimRules.sinceEra(ERA),
            // NOTE: this is a THRESHOLD, not a price. It gates who may even try. What is
            // actually deducted is the engine's `claiming.essence-cost.OLYMPIAN` from
            // config.yml. Athena asks for more than Hermes because she's Athena.
            ClaimRules.essenceCost(essence),
            ClaimRules.queuePriority(),
        ) + extraRules,
        // The gods do not retire. They are still here in the Odyssey, still meddling.
        endurance = Endurance.ETERNAL,
    )

    /**
     * These are claimed, not born — and the gate is **essence**, which is the payoff for
     * the whole spirit system. The players who fought the Titanomachy as sworn soldiers,
     * died, went back to the queue and watched an age turn are the ones who can afford a
     * throne. Time served in the story is how you buy a bigger part in the next one.
     */
    val newcomers = listOf(
        olympian(
            "athena", "Athena", listOf("wisdom", "war done properly", "crafts"),
            "You came out of his head fully armed, which tells you everything about him and rather a lot about you.",
            listOf("counsel"), essence = 60,
        ),
        olympian(
            "apollo", "Apollo", listOf("light", "prophecy", "plague"),
            "You know what happens next. It has never once helped anybody.",
            listOf("oracle"), essence = 60,
        ),
        olympian(
            "artemis", "Artemis", listOf("the hunt", "wild things", "the moon"),
            "You asked for a bow and to be left alone. You got the bow.",
            listOf("hunt"), essence = 50,
        ),
        olympian(
            "hermes", "Hermes", listOf("messages", "thieves", "the road"),
            "You stole a herd of cattle before lunch on the day you were born, and talked your way out of it by dinner.",
            listOf("errand"), essence = 40,
        ),
        olympian(
            "ares", "Ares", listOf("war", "the actual thing"),
            "Not the strategy. The noise, and the mud, and the part nobody writes poems about.",
            listOf("warcry"), essence = 40,
        ),
        olympian(
            "hephaestus", "Hephaestus", listOf("the forge", "fire", "craft"),
            "Thrown off Olympus by your own mother for being ugly. You landed, and you built better than any of them.",
            listOf("smith"), essence = 40,
        ),
        olympian(
            "dionysus", "Dionysus", listOf("wine", "madness", "the crowd"),
            "The last one seated, and the only one who was ever mortal. Both facts matter.",
            listOf("revel"), essence = 40,
        ),
        olympian(
            "aphrodite", "Aphrodite", listOf("desire", "the sea-foam"),
            "You were not born. You were what was left when the sky was cut, and the sea would not let it sink.",
            listOf("desire"), essence = 50,
            extraRules = listOf(fromTheFoam),
        ),
    )

    /**
     * **And at last, everybody else.**
     *
     * Five hundred seats, no gate, no cost. On a 100-player server this is where the
     * server actually lives from now on: not gods, not spirits — *people*, who can pray,
     * and be answered, and be ignored.
     *
     * Set `claiming.default-role: mortal` in the Mythos config once this age begins and
     * new players stop being spirits altogether. Being nobody goes back to being what it
     * should be: a state between roles, not the condition of ninety people.
     */
    val mortal = RoleDefinition(
        id = "mortal",
        displayName = "Mortal",
        tier = RoleTier.MORTAL,
        era = ERA,
        domains = listOf("the world", "asking for things"),
        maxHolders = 500,
        color = "<white>",
        lore = listOf(
            "You will die, and the gods will not particularly notice.",
            "But you can ask them for things — and sometimes, for their own reasons, they answer.",
        ),
        powers = listOf("pray", "sacrifice"),
        claimRules = listOf(ClaimRules.sinceEra(ERA)),
        endurance = Endurance.ETERNAL,
    )

    /**
     * The Titans who FOUGHT are bound in Tartarus; the ones who stayed out of it are not.
     *
     * Done by extending roles this addon didn't write (they belong to EraOfCreation),
     * flipping them to Endurance.ERA at the exact moment this age begins — so the engine
     * retires them for us, with an epithet and a pocket of essence, back into the queue.
     * Zeus was famously lenient with the neutrals. So are we.
     */
    val boundTitans = listOf("kronos", "hyperion", "coeus", "crius", "iapetus")

    companion object {
        const val ERA = "olympian-order"
        const val SKY = "olympus.domain.sky"
        const val SEA = "olympus.domain.sea"
        const val DEAD = "olympus.domain.dead"
        const val OLYMPUS = "olympus.founded"
    }
}
