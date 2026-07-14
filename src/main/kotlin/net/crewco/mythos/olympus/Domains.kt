package net.crewco.mythos.olympus

import net.crewco.mythos.addon.AddonContext
import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.event.PowerUseEvent
import net.crewco.mythos.api.power.Power
import net.crewco.mythos.api.power.PowerContext
import net.crewco.mythos.api.role.RoleTier
import net.crewco.mythos.command.CommandContext.Companion.mm
import net.crewco.mythos.olympus.OlympusContent.Companion.DEAD
import net.crewco.mythos.olympus.OlympusContent.Companion.SEA
import net.crewco.mythos.olympus.OlympusContent.Companion.SKY
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.NamespacedKey
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

const val NEMESIS = "olympus.nemesis"
const val HUBRIS_LIMIT = -50

/** Answering someone is not an act of power. It is manners, and it works anywhere. */
private val SOCIAL = setOf("boon", "permit", "lots", "sacrifice", "pray")

/**
 * **The lots finally mean something.**
 *
 * Before this file, `/power lots` set three flags and broadcast a nice line, and then nothing
 * in the entire game ever read them. Zeus "getting the sky" was a rumour.
 *
 * Now a god's power only works **inside the domain he drew**:
 *
 *  - **Sky** — Zeus cannot throw a thunderbolt with a roof over his head. Go outside.
 *  - **Sea** — Poseidon is a large angry man on dry land. Get near water, or wait for rain.
 *  - **The Dead** — Hades is strongest where the light isn't: the Underworld, or underground.
 *
 * And because the lots are drawn **blind**, none of them chose this. The most powerful being
 * in the world spent one afternoon reaching into a helmet, and the shape of his power for the
 * rest of the mythology was decided by what his hand closed on.
 */
class DomainListener(
    private val mythos: Mythos,
    private val state: OlympusState,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPower(event: PowerUseEvent) {
        val player = event.player
        val role = mythos.roles.roleOf(player.uniqueId) ?: return
        if (role.tier != RoleTier.OLYMPIAN) return

        /*
         * BUG, fixed: domains were cancelling `boon` and `permit` too.
         *
         * Which meant Hades could not answer a prayer unless he happened to be underground at
         * the time, and Heracles could not get permission for Cerberus unless he found the king
         * of the dead standing in a hole. Domains constrain what a god can DO TO the world —
         * not whether he can answer somebody who asked him a question.
         */
        if (event.powerId in SOCIAL) return

        val profile = mythos.profiles.profile(player.uniqueId)
        val complaint = when {
            profile.hasFlag(SKY) && !underOpenSky(player) ->
                "<gray>You are indoors. <dark_gray><i>The sky is not something you can use from under a roof."
            profile.hasFlag(SEA) && !nearWater(player) ->
                "<gray>There is no water here. <dark_gray><i>On dry land you are a large, angry man with a fork."
            profile.hasFlag(DEAD) && !inTheDark(player) ->
                "<gray>You are standing in the light. <dark_gray><i>Nothing of yours works up here."
            else -> null
        } ?: return

        event.isCancelled = true
        player.sendMessage(mm("<red>Not here."))
        player.sendMessage(mm(complaint))
    }

    // We're on the player's own region in a PowerUseEvent, so reading their surroundings is legal.

    private fun underOpenSky(player: Player): Boolean =
        player.world.getHighestBlockYAt(player.location) <= player.location.blockY

    private fun nearWater(player: Player): Boolean {
        if (player.world.hasStorm() && underOpenSky(player)) return true // rain is his too
        val here = player.location
        for (x in -6..6) for (y in -3..3) for (z in -6..6) {
            val block = here.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
            if (block.type == Material.WATER) return true
        }
        return false
    }

    private fun inTheDark(player: Player): Boolean {
        if (mythos.realms.realmOf(player)?.id == "underworld") return true
        return player.location.blockY < 40 || player.location.block.lightFromSky < 4
    }

    // ---- hubris ---------------------------------------------------------------

    /**
     * **Favor was a number nobody read. Now it hunts you.**
     *
     * Raise a hand to a god and your standing with them collapses. Go far enough below zero
     * and *Nemesis* — who is not a metaphor and not a mob spawn, but a thing that finds you —
     * starts arriving.
     */
    @EventHandler(ignoreCancelled = true)
    fun onStrikeAGod(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return

        val god = mythos.roles.roleOf(victim.uniqueId) ?: return
        if (god.tier != RoleTier.OLYMPIAN) return
        val attackerRole = mythos.roles.roleOf(attacker.uniqueId) ?: return
        if (attackerRole.tier != RoleTier.MORTAL) return // gods fight gods; that's just Tuesday

        val profile = mythos.profiles.profile(attacker.uniqueId)
        profile.addFavor(god.id, -20)
        val standing = profile.favorWith(god.id)

        attacker.sendMessage(mm("<red>You raised your hand to ${god.color}${god.displayName}<red>. <gray>They noticed. <dark_gray>($standing)"))

        if (standing <= HUBRIS_LIMIT && !profile.hasFlag(NEMESIS)) {
            profile.setFlag(NEMESIS, true)
            Bukkit.getServer().sendMessage(
                mm("<dark_red>» <white>${attacker.name} <gray>has gone too far. <dark_gray><i>Something has been sent."),
            )
            mythos.chronicle.record("hubris", "<white>${attacker.name} <gray>struck ${god.color}${god.displayName}<gray>. Nemesis was sent.")
        }
    }
}

/**
 * **Nemesis.** Not a punishment you can log out of.
 *
 * She arrives every few minutes, wherever you are, until you make it right. There is exactly
 * one way out, and it is not violence: go to an altar and give something up.
 */
class Nemesis(
    private val mythos: Mythos,
    private val context: AddonContext,
) : Listener {

    private val key = NamespacedKey(context.plugin, "nemesis")

    /**
     * EXPLOIT, patched: Nemesis was a renewable emerald mine.
     *
     * A Vindicator every three minutes, forever, with vanilla drops. The correct response to
     * divine punishment was to build a grinder and get rich. She drops nothing now — she is
     * not a mob, she is a consequence.
     */
    @EventHandler
    fun onNemesisDeath(event: EntityDeathEvent) {
        if (!event.entity.persistentDataContainer.has(key, PersistentDataType.BYTE)) return
        event.drops.clear()
        event.droppedExp = 0
        event.entity.killer?.sendMessage(mm("<dark_gray><i>It falls. It is not gone. <white>/power sacrifice"))
    }

    fun start() {
        context.schedulers.globalRepeating(20 * 60, 20 * 60 * 3) {
            Bukkit.getOnlinePlayers().forEach { player ->
                val profile = mythos.profiles.profile(player.uniqueId)
                if (!profile.hasFlag(NEMESIS)) return@forEach

                context.schedulers.entity(player) {
                    val spot = player.location.clone().add((-6..6).random().toDouble(), 0.0, (-6..6).random().toDouble())
                    val hunter = player.world.spawnEntity(spot, EntityType.VINDICATOR) as LivingEntity
                    hunter.customName(mm("<dark_red>Nemesis"))
                    hunter.isCustomNameVisible = true
                    hunter.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 60.0
                    hunter.health = 60.0
                    hunter.removeWhenFarAway = false
                    hunter.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
                    hunter.addPotionEffect(PotionEffect(PotionEffectType.SPEED, Int.MAX_VALUE, 1, false, false))

                    player.addPotionEffect(PotionEffect(PotionEffectType.UNLUCK, 1200, 0, false, false))
                    player.sendMessage(mm("<dark_red>Something has found you again."))
                    player.sendMessage(mm("<dark_gray><i>You cannot kill your way out of this. <white>/power sacrifice <god>"))
                }
            }
        }
    }
}

/**
 * **Atonement.** The only exit from hubris, and it costs you something real.
 *
 * Stand at an altar with gold in your hand and give it away. Not to a shop — to somebody who
 * may not even be online, and who is under no obligation to forgive you.
 */
class SacrificePower(private val mythos: Mythos) : Power {
    override val id = "sacrifice"
    override val displayName = "Sacrifice"
    override val description = "Give up something real, at an altar, to a god you have wronged. /power sacrifice <god>"
    override val cooldownSeconds = 60

    override fun use(ctx: PowerContext): Boolean {
        val mortal = ctx.player
        val godId = ctx.args.firstOrNull()?.lowercase()
            ?: return false.also { mortal.sendMessage(mm("<red>/power sacrifice <god>")) }

        val god = mythos.roles.definition(godId)
            ?: return false.also { mortal.sendMessage(mm("<red>Nobody by that name is listening.")) }

        val below = mortal.location.clone().subtract(0.0, 1.0, 0.0).block
        if (below.type != Material.GOLD_BLOCK) {
            mortal.sendMessage(mm("<red>Not here. <gray>Gold, under the open sky. <dark_gray><i>The altar is the point."))
            return false
        }

        val hand = mortal.inventory.itemInMainHand
        val worth = when (hand.type) {
            Material.GOLDEN_APPLE -> 40
            Material.GOLD_INGOT -> 15
            Material.GOLD_BLOCK -> 60
            else -> 0
        }
        if (worth == 0) {
            mortal.sendMessage(mm("<red>Empty hands are not an apology. <gray>Gold. An apple. Something you'd rather keep."))
            return false
        }

        val offering = hand.clone()
        offering.amount -= 1
        mortal.inventory.setItemInMainHand(if (offering.amount <= 0) null else offering)

        val profile = mythos.profiles.profile(mortal.uniqueId)
        profile.addFavor(godId, worth)
        mortal.world.strikeLightningEffect(mortal.location)
        mortal.sendMessage(mm("<gold>It burns on the altar. <gray>Standing with ${god.displayName}: <white>${profile.favorWith(godId)}"))

        // Out of hubris only when you are square with EVERYONE you have wronged.
        val stillHated = profile.favor.values.any { it <= HUBRIS_LIMIT }
        if (!stillHated && profile.hasFlag(NEMESIS)) {
            profile.setFlag(NEMESIS, null)
            mortal.sendMessage(mm("<gold>Whatever was following you has stopped."))
            mythos.chronicle.record("hubris", "<white>${mortal.name} <gray>made it right, and Nemesis went home.")
        }

        // The god actually hears about it. They can be gracious, or they can not bother.
        mythos.roles.holders(godId).mapNotNull { Bukkit.getPlayer(it) }.forEach { deity ->
            deity.sendMessage(mm("<gray>${mortal.name} burned something of theirs on your altar. <dark_gray><i>You could answer. /power boon"))
        }
        return true
    }
}
