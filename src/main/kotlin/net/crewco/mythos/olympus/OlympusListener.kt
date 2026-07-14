package net.crewco.mythos.olympus

import net.crewco.mythos.addon.AddonContext
import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.event.EraAdvancedEvent
import net.crewco.mythos.api.event.PowerUseEvent
import net.crewco.mythos.api.event.RoleClaimedEvent
import net.crewco.mythos.api.role.Endurance
import net.crewco.mythos.api.role.RoleTier
import net.crewco.mythos.command.CommandContext.Companion.mm
import net.crewco.mythos.olympus.OlympusContent.Companion.ERA
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import net.crewco.mythos.api.event.MythosResetEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class OlympusListener(
    private val mythos: Mythos,
    private val context: AddonContext,
    private val content: OlympusContent,
    private val state: OlympusState,
) : Listener {

    /**
     * **Zeus's decree, enforced.**
     *
     * The engine fires PowerUseEvent before every power in the game, cancellable — so a
     * law forbidding the gods to intervene is nine lines, and it applies to powers this
     * addon has never heard of, from addons written after it. That's the whole design
     * paying off: the Iliad's "Zeus decides each morning who is allowed to help" is
     * *already implemented*, three chapters early, by accident.
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onPower(event: PowerUseEvent) {
        if (!state.decreeActive) return
        val role = mythos.roles.roleOf(event.player.uniqueId) ?: return
        if (role.id == "zeus") return // he wrote it; it doesn't apply to him. Obviously.

        val divine = role.tier in setOf(RoleTier.OLYMPIAN, RoleTier.TITAN, RoleTier.PRIMORDIAL, RoleTier.CHTHONIC)
        if (!divine) return

        event.isCancelled = true
        event.player.sendMessage(mm("<red>Zeus has forbidden it. <gray>You feel the refusal like a hand on your chest."))
    }

    @EventHandler
    fun onClaimed(event: RoleClaimedEvent) {
        if (mythos.eras.currentId() != ERA) return

        // A seat contributed by another addon brings its own beat with it; complete()
        // no-ops when no such objective exists, so we don't care who added what.
        mythos.eras.complete(ERA, "seat_${event.role.id}", "${event.role.displayName} took a throne")

        if (event.role.id == "aphrodite") {
            mythos.chronicle.record(
                "story",
                "<light_purple>Aphrodite <gray>came out of the water where the sky's blood fell. " +
                    "<dark_gray><i>Nobody had planned for her.",
            )
            mythos.eras.complete(ERA, "aphrodite_rises", "something beautiful came out of the water")
        }

        val seated = mythos.roles.definitions()
            .filter { it.tier == RoleTier.OLYMPIAN }
            .count { mythos.roles.holders(it.id).isNotEmpty() }

        if (seated >= mythos.dev.threshold(12)) {
            mythos.eras.complete(ERA, "the_twelve", "twelve thrones, and an argument on every one")
        } else if (event.role.tier == RoleTier.OLYMPIAN) {
            Bukkit.getServer().sendMessage(
                mm("<dark_gray>» <gray>${12 - seated} throne(s) still empty. <dark_gray><i>The gods are not a fixed list; they never were."),
            )
        }
    }

    @EventHandler
    fun onEra(event: EraAdvancedEvent) {
        if (event.to.id != ERA) return

        /*
         * THE TITANS WHO FOUGHT.
         *
         * Kronos, Hyperion, Coeus, Crius and Iapetus belong to EraOfCreation — a jar this
         * one doesn't import, depend on, or know the version of. We flip them to
         * Endurance.ERA *right now*, inside the era-advance handler, and the engine's
         * retirement pass — which runs immediately AFTER this event — dissolves them back
         * into the spirit world with an epithet and a pocket of essence.
         *
         * Mythologically: bound in Tartarus. Mechanically: back in the queue, rich, and
         * first in line for a throne. Zeus was lenient with the ones who stayed out of it,
         * and so are we: Oceanus, Themis, Mnemosyne and the rest keep their names.
         */
        var bound = 0
        content.boundTitans.forEach { titan ->
            if (mythos.roles.extend(titan) { it.copy(endurance = Endurance.ERA) }) bound++
        }
        if (bound > 0) {
            context.logger.info("$bound Titan(s) marked for binding. The engine will retire them as the age turns.")
            context.schedulers.globalDelayed(60) {
                mythos.chronicle.record(
                    "story",
                    "<gray>The Titans who fought were put where they cannot reach the sky. " +
                        "<dark_gray><i>The ones who stayed out of it kept their names.",
                )
                mythos.eras.complete(ERA, "the_titans_bound", "the losers went under the world")
            }
        }

        // The three brothers have one job and it is not obvious to them yet.
        context.schedulers.globalDelayed(80) {
            mythos.roles.holders("zeus").mapNotNull { Bukkit.getPlayer(it) }.forEach { zeus ->
                context.schedulers.entity(zeus) {
                    zeus.sendMessage(mm("<yellow>The world is yours, and your brothers are watching you not share it."))
                    zeus.sendMessage(mm("<white>/power lots <dark_gray>— divide it blind. <dark_gray><i>You don't get to pick the sky. You just get it."))
                    zeus.sendMessage(mm("<white>/power olympus <dark_gray>— somewhere above y=150. Build first; found it second."))
                }
            }
        }

        context.logger.info(
            "Mortals are now claimable. Set `claiming.default-role: mortal` in plugins/Mythos/config.yml " +
                "so new players are born into the world instead of hovering above it.",
        )
    }

    /** Olympus is somewhere. After a reset it needs to be nowhere. */
    @EventHandler
    fun onReset(event: MythosResetEvent) {
        if (event.scope == MythosResetEvent.Scope.PLAYER) return
        state.clear()
        context.logger.info("Olympus has been unbuilt.")
    }
}
