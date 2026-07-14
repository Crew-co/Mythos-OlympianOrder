package net.crewco.mythos.olympus

import net.crewco.mythos.addon.AddonContext
import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.power.Power
import net.crewco.mythos.api.power.PowerContext
import net.crewco.mythos.api.story.Beat
import net.crewco.mythos.api.story.beats
import net.crewco.mythos.api.story.line
import net.crewco.mythos.api.story.title
import net.crewco.mythos.command.CommandContext.Companion.mm
import net.crewco.mythos.olympus.OlympusContent.Companion.DEAD
import net.crewco.mythos.olympus.OlympusContent.Companion.ERA
import net.crewco.mythos.olympus.OlympusContent.Companion.SEA
import net.crewco.mythos.olympus.OlympusContent.Companion.SKY
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

/** Zeus divides the world. He does not get to choose which piece he gets — that's the point. */
class LotsPower(private val mythos: Mythos, private val context: AddonContext) : Power {
    override val id = "lots"
    override val displayName = "Draw Lots"
    override val description = "Divide the world three ways, blind. /power lots"
    override val cooldownSeconds = 0

    override fun use(ctx: PowerContext): Boolean {
        val zeus = ctx.player
        if (mythos.eras.isComplete(ERA, "the_lots")) {
            zeus.sendMessage(mm("<red>It's already divided. That's what 'divided' means."))
            return false
        }
        val brothers = listOf("zeus", "poseidon", "hades")
            .associateWith { mythos.roles.holders(it).firstOrNull() }
            .filterValues { it != null }

        // Three brothers, or — if you are testing this alone at two in the morning — one.
        if (brothers.size < mythos.dev.threshold(3)) {
            zeus.sendMessage(mm("<red>All three of you have to be here. It's a lottery, not a decree."))
            return false
        }

        // Blind. Zeus does not get to pick the sky; he just gets it, and spends the rest
        // of the mythology insisting that proves something.
        val domains = listOf(SKY to "the sky", SEA to "the sea", DEAD to "the dead").shuffled()
        val drawn = brothers.keys.toList().zip(domains)  // zip truncates: one brother draws one lot

        context.schedulers.global {
            drawn.forEach { (roleId, domain) ->
                val uuid = brothers[roleId] ?: return@forEach
                mythos.profiles.profile(uuid).setFlag(domain.first, true)
            }
            mythos.narrator.tell(
                listOf(Beat(20, text = "<dark_gray>» <gray>Three lots, in a helmet. Nobody looks.")) +
                    drawn.map { (roleId, domain) ->
                        val name = mythos.roles.definition(roleId)?.displayName ?: roleId
                        Beat(
                            delayTicks = 40,
                            text = "<dark_gray>   <yellow>$name <gray>draws <white>${domain.second}<gray>.",
                            sound = "minecraft:block.note_block.chime",
                        )
                    },
            )
            mythos.chronicle.record("story", "<gray>The world was divided by lot: ${drawn.joinToString { "${it.first} → ${it.second.second}" }}.")
            mythos.eras.complete(ERA, "the_lots", "the world was divided three ways, blind")
        }
        return true
    }
}

/** Somewhere to put the thrones. */
class OlympusPower(private val mythos: Mythos, private val context: AddonContext, private val state: OlympusState) : Power {
    override val id = "olympus"
    override val displayName = "Raise Olympus"
    override val description = "Found the seat of the gods where you stand. It must be high. /power olympus"
    override val cooldownSeconds = 0

    override fun use(ctx: PowerContext): Boolean {
        val zeus = ctx.player
        if (mythos.eras.isComplete(ERA, "olympus_raised")) {
            zeus.sendMessage(mm("<red>Olympus stands. Building a second one would be embarrassing."))
            return false
        }
        // You cannot find Olympus in a field. Go to Olympus — it is a *world*, and it has
        // been sitting there empty since the server generated it, waiting for a throne.
        if (mythos.realms.realmOf(zeus)?.id != "olympus") {
            zeus.sendMessage(mm("<red>Not here. <gray>Olympus is not a hill you climb; it is somewhere else entirely."))
            zeus.sendMessage(mm("<dark_gray><i>You are a god. You can already go there. <white>/mythos realm olympus"))
            return false
        }

        state.olympus = zeus.location.clone()
        state.save()

        val where = "${zeus.location.blockX}, ${zeus.location.blockY}, ${zeus.location.blockZ}"
        mythos.narrator.tell(
            beats {
                title("<gold>Olympus", "<gray>above the weather, above the argument", delayTicks = 20, sound = "minecraft:block.beacon.power_select")
                line("<gray>The seat of the gods stands at <white>$where<gray>.", delayTicks = 45)
                line("<dark_gray><i>From up there, everything below looks small enough to be simple.", delayTicks = 50)
            },
        )
        mythos.chronicle.record("story", "<gray>Olympus was raised at $where.")
        mythos.eras.complete(ERA, "olympus_raised", "the thrones were set above the clouds")
        return true
    }

}

/**
 * **Zeus forbids intervention.**
 *
 * For a few minutes no god may use a power. It's enforced by cancelling PowerUseEvent —
 * the same hook a rival god would use to smother one spell. Here it's a *law*, and the
 * Iliad is going to be built on exactly this: Zeus deciding, each morning, who is allowed
 * to help.
 */
class DecreePower(private val mythos: Mythos, private val state: OlympusState,private val context: AddonContext) : Power {
    override val id = "decree"
    override val displayName = "Forbid Intervention"
    override val description = "No god may act for three minutes. Yourself excepted, obviously. /power decree"
    override val cooldownSeconds = 900

    override fun use(ctx: PowerContext): Boolean {
        state.decreeUntil = System.currentTimeMillis() + 180_000
        context.schedulers.global {Bukkit.getServer().sendMessage(mm("<dark_gray>» <yellow>Zeus <gray>forbids the gods to intervene. <dark_gray><i>For three minutes, the mortals are on their own."))
        }
        mythos.chronicle.record("story", "<yellow>Zeus <gray>forbade the gods to intervene.")
        return true
    }
}

/** Apollo knows what happens next. It has never once helped anybody. */
class OraclePower(private val mythos: Mythos,private val context: AddonContext) : Power {
    override val id = "oracle"
    override val displayName = "The Oracle"
    override val description = "Drag one hidden thing about this age into the light. /power oracle"
    override val cooldownSeconds = 600

    override fun use(ctx: PowerContext): Boolean {
        val apollo = ctx.player
        val era = mythos.eras.current() ?: return false
        val hidden = mythos.eras.objectives(era.id)
            .filter { it.hidden && !mythos.eras.isComplete(era.id, it.id) }

        if (hidden.isEmpty()) {
            apollo.sendMessage(mm("<gray>Nothing is hidden from you. <dark_gray><i>That is its own kind of curse."))
            return false
        }
        val revealed = hidden.random()
        context.schedulers.global {         Bukkit.getServer().sendMessage(mm("<dark_gray>» <yellow>Apollo <gray>speaks, and it is worse than not knowing:"))
            Bukkit.getServer().sendMessage(mm("<dark_gray>   <white><i>${revealed.description}")) }
        mythos.chronicle.record("story", "<yellow>Apollo <gray>prophesied: <i>${revealed.description}")
        return true
    }
}

/** Athena arms a mortal, and expects them to think first. */
class CounselPower(private val mythos: Mythos, private val context: AddonContext) : Power {
    override val id = "counsel"
    override val displayName = "Counsel"
    override val description = "Arm a mortal, and expect them to think. /power counsel <player>"
    override val cooldownSeconds = 120

    override fun use(ctx: PowerContext): Boolean {
        val athena = ctx.player
        val target = ctx.args.firstOrNull()?.let { Bukkit.getPlayerExact(it) }
            ?: return false.also { athena.sendMessage(mm("<red>/power counsel <player>")) }

        context.schedulers.entity(target) {
            target.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 1200, 0))
            target.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 1200, 0))
            target.sendMessage(mm("<yellow>Athena <gray>is at your shoulder. <dark_gray><i>She will be extremely disappointed if you charge."))
        }
        mythos.profiles.profile(target.uniqueId).addFavor("athena", 5)
        return true
    }
}

/** Hermes goes wherever he likes, which is the entire job. */
class ErrandPower(private val context: AddonContext) : Power {
    override val id = "errand"
    override val displayName = "Errand"
    override val description = "Be somewhere else, immediately. /power errand <player>"
    override val cooldownSeconds = 60

    override fun use(ctx: PowerContext): Boolean {
        val hermes = ctx.player
        val target = ctx.args.firstOrNull()?.let { Bukkit.getPlayerExact(it) }
            ?: return false.also { hermes.sendMessage(mm("<red>/power errand <player>")) }

        // Folia: teleportAsync is the only thread-safe way across regions, and the target's
        // location must be read on the target's own region — so ask THEM where they are.
        context.schedulers.entity(target) {
            val destination = target.location
            hermes.teleportAsync(destination).thenRun {
                context.schedulers.entity(hermes) {
                    hermes.sendMessage(mm("<gray>You were never not here."))
                }
            }
            target.sendMessage(mm("<yellow>Hermes <gray>is standing behind you. <dark_gray><i>He has been for a moment."))
        }
        return true
    }
}

/** Artemis marks you, and that is the entire hunt. */
class HuntPower(private val context: AddonContext) : Power {
    override val id = "hunt"
    override val displayName = "Mark the Quarry"
    override val description = "Mark someone. Everyone can see them now. /power hunt <player>"
    override val cooldownSeconds = 90

    override fun use(ctx: PowerContext): Boolean {
        val artemis = ctx.player
        val target = ctx.args.firstOrNull()?.let { Bukkit.getPlayerExact(it) }
            ?: return false.also { artemis.sendMessage(mm("<red>/power hunt <player>")) }

        context.schedulers.entity(target) {
            target.isGlowing = true
            target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 1200, 0))
            target.sendMessage(mm("<gray>Something in the treeline has decided about you."))
        }
        Bukkit.getServer().sendMessage(mm("<dark_gray>» <yellow>Artemis <gray>has marked <white>${target.name}<gray>."))
        return true
    }
}

/** Ares is not the strategy. Ares is the noise. */
class WarcryPower(private val context: AddonContext) : Power {
    override val id = "warcry"
    override val displayName = "War Cry"
    override val description = "Everyone near you fights harder and thinks less. /power warcry"
    override val cooldownSeconds = 120

    override fun use(ctx: PowerContext): Boolean {
        val ares = ctx.player
        ares.getNearbyEntities(16.0, 8.0, 16.0).filterIsInstance<Player>().forEach { near ->
            near.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 600, 0))
            near.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 600, 0))
            near.sendMessage(mm("<red>Your blood is up and your judgement is gone."))
        }
        ares.world.playSound(ares.location, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 1f, 0.8f)
        return true
    }
}

/** Hephaestus makes things that outlast the people they're made for. */
class SmithPower : Power {
    override val id = "smith"
    override val displayName = "The Forge"
    override val description = "Make the thing in your hand permanent. /power smith"
    override val cooldownSeconds = 300

    override fun use(ctx: PowerContext): Boolean {
        val smith = ctx.player
        val item = smith.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            smith.sendMessage(mm("<red>Your hands are empty. Even you can't work with that."))
            return false
        }
        item.editMeta { meta ->
            meta.isUnbreakable = true
            meta.lore(listOf(mm("<dark_gray><i>Made on Olympus. It will outlast you.")))
        }
        smith.sendMessage(mm("<gray>It will not break. <dark_gray><i>Everything else about it still might."))
        return true
    }
}

/** Aphrodite does not ask. */
class DesirePower(private val context: AddonContext) : Power {
    override val id = "desire"
    override val displayName = "Desire"
    override val description = "They come to you. They will not be able to explain why. /power desire <player>"
    override val cooldownSeconds = 120

    override fun use(ctx: PowerContext): Boolean {
        val aphrodite = ctx.player
        val target = ctx.args.firstOrNull()?.let { Bukkit.getPlayerExact(it) }
            ?: return false.also { aphrodite.sendMessage(mm("<red>/power desire <player>")) }

        val destination = aphrodite.location
        context.schedulers.entity(target) {
            val pull: Vector = destination.toVector().subtract(target.location.toVector())
            if (pull.lengthSquared() > 1) target.velocity = pull.normalize().multiply(1.6).setY(0.6)
            target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1))
            target.sendMessage(mm("<light_purple>You have made a decision, and it was not you who made it."))
        }
        return true
    }
}

/** Dionysus arrives and the evening stops being anybody's idea. */
class RevelPower : Power {
    override val id = "revel"
    override val displayName = "Revel"
    override val description = "The crowd stops being a group of people. /power revel"
    override val cooldownSeconds = 180

    override fun use(ctx: PowerContext): Boolean {
        val dionysus = ctx.player
        dionysus.getNearbyEntities(20.0, 10.0, 20.0).filterIsInstance<Player>().forEach { near ->
            near.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 400, 1))
            near.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 300, 0))
            near.sendMessage(mm("<light_purple>You are having a wonderful time. <dark_gray><i>You will not remember most of it."))
        }
        return true
    }
}

/** Hades is not the villain. He does the job nobody else wanted and does it properly. */
class JudgePower(private val mythos: Mythos) : Power {
    override val id = "judge"
    override val displayName = "Judge the Dead"
    override val description = "Weigh a spirit's life. The worthy are paid. /power judge <spirit>"
    override val cooldownSeconds = 60

    override fun use(ctx: PowerContext): Boolean {
        val hades = ctx.player
        val target = ctx.args.firstOrNull()?.let { Bukkit.getPlayerExact(it) }
            ?: return false.also { hades.sendMessage(mm("<red>/power judge <spirit>")) }

        if (!mythos.spirits.isSpirit(target.uniqueId)) {
            hades.sendMessage(mm("<red>${target.name} is still alive. Not your jurisdiction. Yet."))
            return false
        }
        val profile = mythos.profiles.profile(target.uniqueId)
        val lived = profile.pastRoles.size
        val reward = 10 + lived * 5

        mythos.spirits.grantEssence(target.uniqueId, reward, "judged worthy by Hades")
        hades.sendMessage(mm("<gray>${target.name} wore <white>$lived</white> name(s) in life. You give them <white>$reward</white> essence."))
        target.sendMessage(mm("<dark_gray>The king of the dead has read your whole life and did not look bored."))
        return true
    }
}
