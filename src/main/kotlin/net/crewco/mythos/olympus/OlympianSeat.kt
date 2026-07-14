package net.crewco.mythos.olympus

import net.crewco.mythos.api.role.RoleTier

/**
 * **A seat at the table, for a god nobody has written yet.**
 *
 * The Twelve are not a fixed list — the Greeks themselves couldn't agree on it (Hestia
 * gave hers up to Dionysus, depending on who you ask). So this addon doesn't hard-code
 * the pantheon; it opens a point and lets any jar, ever, seat a god:
 *
 * ```kotlin
 * // In a Persephone addon, written years later.
 * mythos.extensions.contribute(
 *     OlympianSeat.POINT,
 *     OlympianSeat(
 *         id = "persephone",
 *         name = "Persephone",
 *         domains = listOf("spring", "the dead"),
 *         lore = "Six seeds. Six months. Nobody asked you.",
 *         powers = listOf("bloom"),
 *         essenceCost = 60,
 *     ),
 * )
 * ```
 *
 * The seat becomes a claimable role *and* an optional beat of this era, and the
 * Chronicle records who took it. Load order doesn't matter: the engine replays
 * contributions, so the Persephone jar may enable before or after this one.
 */
data class OlympianSeat(
    val id: String,
    val name: String,
    val domains: List<String>,
    val lore: String,
    /** Register these with `mythos.powers` before you contribute the seat. */
    val powers: List<String> = emptyList(),
    val tier: RoleTier = RoleTier.OLYMPIAN,
    /** Essence a spirit must burn to take it. The war veterans have it; the newborn don't. */
    val essenceCost: Int = 40,
) {
    companion object {
        const val POINT = "olympus:seats"
    }
}
