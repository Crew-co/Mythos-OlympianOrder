package net.crewco.mythos.olympus

import net.crewco.mythos.addon.AddonBase
import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.era.Objective
import net.crewco.mythos.api.ext.consume
import net.crewco.mythos.api.role.ClaimRules
import net.crewco.mythos.api.role.Endurance
import net.crewco.mythos.api.role.RoleDefinition
import net.crewco.mythos.api.role.RoleTier
import net.crewco.mythos.api.role.Succession
import net.crewco.mythos.olympus.OlympusContent.Companion.ERA
import java.io.File

/**
 * Story #3 — the age when the world stops being *made* and starts being *governed*.
 *
 * And, far more importantly for a server with a hundred people on it: the age when there
 * is finally somebody to govern. Mortals arrive here. From this chapter on, most players
 * are neither gods nor spirits — they're *people*, who can pray, and be answered, and be
 * ignored, and remember which.
 */
class OlympianOrderAddon : AddonBase() {

    override fun onEnable() {
        val mythos = Mythos.from(context)
        val content = OlympusContent(mythos)
        val prayers = Prayers()
        val state = OlympusState(File(context.dataFolder, "olympus.yml"))

        // Two worlds. One that mortals are put back down from, and one they end up in anyway.
        mythos.realms.register(content.OLYMPUS)
        mythos.realms.register(content.UNDERWORLD)

        mythos.eras.register(content.era)
        content.newcomers.forEach(mythos.roles::register)
        mythos.roles.register(content.mortal)

        mythos.powers.register(LotsPower(mythos, context))
        mythos.powers.register(OlympusPower(mythos, context, state))
        mythos.powers.register(AscentPower(mythos, context))
        mythos.powers.register(DecreePower(mythos, state))
        mythos.powers.register(OraclePower(mythos))
        mythos.powers.register(CounselPower(mythos, context))
        mythos.powers.register(ErrandPower(context))
        mythos.powers.register(HuntPower(context))
        mythos.powers.register(WarcryPower(context))
        mythos.powers.register(SmithPower())
        mythos.powers.register(DesirePower(context))
        mythos.powers.register(RevelPower())
        mythos.powers.register(JudgePower(mythos))
        mythos.powers.register(PrayPower(mythos, context, prayers))
        mythos.powers.register(BoonPower(mythos, context, prayers))
        mythos.powers.register(SacrificePower(mythos))

        context.registerListener(OlympusListener(mythos, context, content, state))

        // The lots, enforced. And favor, which used to be a number nobody read, now hunts you.
        context.registerListener(DomainListener(mythos, state))

        val nemesis = Nemesis(mythos, context)
        context.registerListener(nemesis)
        nemesis.start()
        Faithlessness(mythos, context, prayers).start()

        // Any addon, ever, can seat a god at this table. Replayed contributions mean the
        // contributor may load before or after us; we don't care and neither should they.
        mythos.extensions.consume<OlympianSeat>(OlympianSeat.POINT) { seat ->
            mythos.roles.register(
                RoleDefinition(
                    id = seat.id,
                    displayName = seat.name,
                    tier = seat.tier,
                    era = ERA,
                    domains = seat.domains,
                    color = "<yellow>",
                    lore = listOf(seat.lore),
                    powers = seat.powers + "boon",
                    claimRules = listOf(
                        ClaimRules.sinceEra(ERA),
                        ClaimRules.essenceCost(seat.essenceCost),
                        ClaimRules.queuePriority(),
                    ),
                    succession = Succession.QUEUE,
                    endurance = Endurance.ETERNAL,
                ),
            )
            mythos.eras.addObjective(ERA, Objective("seat_${seat.id}", "${seat.name} takes a throne", optional = true))
            context.logger.info("${seat.name} was seated by another addon.")
        }

        // One tick on, every other jar has registered its cast — so "did Titanomachy give
        // us Zeus?" finally has a truthful answer. If it didn't, we make our own; this
        // chapter can be told standalone from `/mythos advance olympian-order`.
        context.schedulers.globalDelayed(1) {
            registerFallbacks(mythos)
            grantRule(mythos)
        }

        context.logger.info("The Olympian Order is ready. Twelve thrones, and mortals to look up at them.")
    }

    /** The three brothers, if the war that produced them isn't installed. */
    private fun registerFallbacks(mythos: Mythos) {
        val elders = listOf(
            Triple("zeus", "Zeus", listOf("sky", "oath", "kingship")),
            Triple("hera", "Hera", listOf("marriage", "sovereignty")),
            Triple("poseidon", "Poseidon", listOf("sea", "earthquake")),
            Triple("hades", "Hades", listOf("the dead", "wealth")),
            Triple("demeter", "Demeter", listOf("grain", "the seasons")),
            Triple("hestia", "Hestia", listOf("the hearth")),
        )
        elders.forEach { (id, name, domains) ->
            if (mythos.roles.definition(id) != null) return@forEach
            mythos.roles.register(
                RoleDefinition(
                    id = id, displayName = name, tier = RoleTier.OLYMPIAN, era = ERA,
                    domains = domains, color = "<yellow>",
                    lore = listOf("You do not remember the war. On this world, there wasn't one."),
                    powers = listOf("boon"),
                    claimRules = listOf(ClaimRules.sinceEra(ERA), ClaimRules.queuePriority()),
                ),
            )
        }
    }

    /**
     * The rulers of the world get the powers of *ruling* it — which the addon that made
     * them (Titanomachy) had no reason to give them, because in that story they were
     * children hiding in a cave.
     *
     * `extend` returns false if the role isn't there. That's not an error; it's a story
     * that isn't being told on this server.
     */
    private fun grantRule(mythos: Mythos) {
        mythos.roles.extend("zeus") { it.copy(powers = (it.powers + listOf("lots", "olympus", "ascent", "decree", "boon")).distinct()) }
        mythos.roles.extend("poseidon") { it.copy(powers = (it.powers + "boon").distinct()) }
        mythos.roles.extend("hades") { it.copy(powers = (it.powers + listOf("judge", "boon")).distinct()) }
        mythos.roles.extend("hera") { it.copy(powers = (it.powers + "boon").distinct()) }
        mythos.roles.extend("demeter") { it.copy(powers = (it.powers + "boon").distinct()) }
        mythos.roles.extend("hestia") { it.copy(powers = (it.powers + "boon").distinct()) }
    }
}
