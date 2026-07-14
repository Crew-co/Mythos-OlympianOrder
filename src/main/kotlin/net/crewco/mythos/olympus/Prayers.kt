package net.crewco.mythos.olympus

import net.crewco.mythos.addon.AddonContext
import net.crewco.mythos.api.Mythos
import net.crewco.mythos.api.power.Power
import net.crewco.mythos.api.power.PowerContext
import net.crewco.mythos.command.CommandContext.Companion.mm
import net.crewco.mythos.olympus.OlympusContent.Companion.ERA
import java.util.concurrent.atomic.AtomicLong
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * The transaction the whole rest of the mythology runs on: **a mortal asks, and a god
 * decides whether to care.**
 *
 * Not a shop. There's no guaranteed exchange rate — the god has to actually be online,
 * actually read it, and actually choose to answer. Favor accumulates on both sides, and
 * favor is what the Trojan War will be fought with.
 */
class Prayers {

    data class Prayer(val mortal: UUID, val god: String, val at: Long, val words: String)

    /** godRoleId → the mortals waiting, oldest first. */
    private val queues = ConcurrentHashMap<String, ConcurrentLinkedDeque<Prayer>>()

    fun offer(god: String, mortal: UUID, words: String) {
        queues.getOrPut(god) { ConcurrentLinkedDeque() }.addLast(Prayer(mortal, god, System.currentTimeMillis(), words))
    }

    fun next(god: String): Prayer? = queues[god]?.pollFirst()

    fun waiting(god: String): Int = queues[god]?.size ?: 0

    /** Prayers older than [ageMs] that nobody answered. Removed as they're returned. */
    fun stale(ageMs: Long): List<Prayer> {
        val cutoff = System.currentTimeMillis() - ageMs
        val old = ArrayList<Prayer>()
        queues.values.forEach { queue ->
            while (true) {
                val head = queue.peekFirst() ?: break
                if (head.at > cutoff) break
                queue.pollFirst()
                old += head
            }
        }
        return old
    }

    fun clear() = queues.clear()
}

/**
 * **Not answering is an answer, and it costs you.**
 *
 * A prayer nobody replies to for ten minutes doesn't just expire — the mortal's faith in that
 * god drops, and the god's standing *with mortals* drops with it. Which matters, because in
 * three chapters' time the Trojan War is going to be fought by armies who only fight as hard
 * as their gods are worth.
 *
 * Ignoring your worshippers is a strategy. It is simply not a free one.
 */
class Faithlessness(
    private val mythos: Mythos,
    private val context: AddonContext,
    private val prayers: Prayers,
) {
    fun start() {
        context.schedulers.globalRepeating(20 * 60 * 5, 20 * 60 * 5) {
            prayers.stale(10 * 60 * 1000).forEach { prayer ->
                val god = mythos.roles.definition(prayer.god) ?: return@forEach

                mythos.profiles.profile(prayer.mortal).addFavor(prayer.god, -5)
                mythos.roles.holders(prayer.god).forEach { deity ->
                    mythos.profiles.profile(deity).addFavor("mortals", -3)
                }

                Bukkit.getPlayer(prayer.mortal)?.let { mortal ->
                    context.schedulers.entity(mortal) {
                        mortal.sendMessage(mm("<dark_gray><i>You waited. ${god.displayName} did not answer."))
                        mortal.sendMessage(mm("<dark_gray><i>You will remember that. So will everyone you tell."))
                    }
                }
                mythos.roles.holders(prayer.god).mapNotNull { Bukkit.getPlayer(it) }.forEach { deity ->
                    context.schedulers.entity(deity) {
                        deity.sendMessage(mm("<gray>A prayer went unanswered long enough that they stopped waiting."))
                    }
                }
            }
        }
    }
}

/** Olympus's own little state: where the mountain is, and whether Zeus has forbidden anything. */
class OlympusState(private val file: File) {

    private val yaml = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()

    var olympus: Location? = yaml.getLocation("olympus")
    val decree = AtomicLong(0)

    var decreeUntil: Long
        get() = decree.get()
        set(value) = decree.set(value)

    val decreeActive: Boolean get() = System.currentTimeMillis() < decree.get()

    @Synchronized
    fun save() {
        yaml.set("olympus", olympus)
        runCatching { yaml.save(file) }
    }

    fun clear() {
        olympus = null
        decree.set(0)
        save()
    }
}

/**
 * A mortal prays. They have to be *at an altar* — gold, under the open sky — because a
 * prayer you can send from anywhere is a text message.
 */
class PrayPower(
    private val mythos: Mythos,
    private val context: AddonContext,
    private val prayers: Prayers,
) : Power {
    override val id = "pray"
    override val displayName = "Pray"
    override val description = "Ask a god for something. Stand on gold, under the sky. /power pray <god> [words]"
    override val cooldownSeconds = 120

    override fun use(ctx: PowerContext): Boolean {
        val mortal = ctx.player
        val godId = ctx.args.firstOrNull()?.lowercase()
            ?: return false.also { mortal.sendMessage(mm("<red>/power pray <god> [what you want]")) }

        val god = mythos.roles.definition(godId)
        if (god == null || god.tier != net.crewco.mythos.api.role.RoleTier.OLYMPIAN) {
            mortal.sendMessage(mm("<red>Nobody by that name is listening."))
            return false
        }

        // Same region as us — safe to read blocks here.
        val below = mortal.location.clone().subtract(0.0, 1.0, 0.0).block
        if (below.type != Material.GOLD_BLOCK) {
            mortal.sendMessage(mm("<red>You are not at an altar. <gray>Gold, under the open sky. <dark_gray><i>They can tell the difference."))
            return false
        }
        if (mortal.location.block.lightFromSky < 10) {
            mortal.sendMessage(mm("<red>They can't see you in here. <gray>Build it where the sky is."))
            return false
        }

        val words = ctx.args.drop(1).joinToString(" ").ifBlank { "help me" }
        prayers.offer(godId, mortal.uniqueId, words)
        mythos.profiles.profile(mortal.uniqueId).addFavor(godId, 1)

        mortal.sendMessage(mm("<gray>You say it out loud. <dark_gray><i>Nothing happens, which is not the same as nobody hearing."))

        // The god is told, wherever they are. Whether they care is entirely up to them.
        mythos.roles.holders(godId).mapNotNull { Bukkit.getPlayer(it) }.forEach { deity ->
            context.schedulers.entity(deity) {
                deity.sendMessage(mm(""))
                deity.sendMessage(mm("<dark_gray>» <white>${mortal.name} <gray>is praying to you:"))
                deity.sendMessage(mm("<dark_gray>   <white><i>\"$words\""))
                deity.sendMessage(mm("<gray>   <white>/power boon <dark_gray>to answer · <dark_gray><i>or don't. That's a message too."))
                deity.sendMessage(mm(""))
            }
        }
        mythos.chronicle.record("prayer", "<white>${mortal.name} <gray>prayed to ${god.color}${god.displayName}<gray>: <i>\"$words\"")
        mythos.eras.complete(ERA, "first_prayer", "somebody asked, out loud, for help")
        return true
    }
}

/** A god answers. Or doesn't — which is also an answer, and is remembered. */
class BoonPower(
    private val mythos: Mythos,
    private val context: AddonContext,
    private val prayers: Prayers,
) : Power {
    override val id = "boon"
    override val displayName = "Answer a Prayer"
    override val description = "Grant the oldest prayer waiting for you. /power boon"
    override val cooldownSeconds = 30

    override fun use(ctx: PowerContext): Boolean {
        val god = ctx.player
        val role = mythos.roles.roleOf(god.uniqueId) ?: return false

        val prayer = prayers.next(role.id) ?: run {
            god.sendMessage(mm("<gray>Nobody is asking you for anything. <dark_gray><i>Enjoy it; it won't last."))
            return false
        }
        val supplicant = prayer.mortal
        val mortal = Bukkit.getPlayer(supplicant)
        if (mortal == null) {
            god.sendMessage(mm("<gray>They stopped waiting. <dark_gray><i>People do."))
            return false
        }

        context.schedulers.entity(mortal) {
            mortal.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 300, 1))
            mortal.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 1200, 0))
            mortal.sendMessage(mm(""))
            mortal.sendMessage(mm("${role.color}${role.displayName} <gray>has answered you."))
            mortal.sendMessage(mm("<dark_gray><i>You will spend the rest of your life telling people about this."))
            mortal.sendMessage(mm(""))
        }
        mythos.profiles.profile(supplicant).addFavor(role.id, 10)
        mythos.profiles.profile(god.uniqueId).addFavor("mortals", 5)
        mythos.chronicle.record("prayer", "${role.color}${role.displayName} <gray>answered <white>${mortal.name}<gray>.")
        god.sendMessage(mm("<gray>You answer. <dark_gray><i>They will build you something for this."))
        return true
    }
}
