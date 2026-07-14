# OlympianOrder

Three brothers divide the world by lot. Olympus is raised. Mortals appear, and start
asking for things. **Story #3 — the era `olympian-order`.**

A story addon for the [Mythos](https://github.com/Crew-co/Mythos) engine.

```kotlin
compileOnly("net.crewco:mythos-addon-api:0.1.0")
```

## The one that changes the server

This is where **mortals** arrive: 500 seats, no gate, no cost. From this chapter on most
of your players are neither gods nor spirits — they're *people*.

Set this in `plugins/Mythos/config.yml` once the age begins:

```yaml
claiming:
  default-role: "mortal"
```

New players are now born into the world instead of hovering above it, and the spirit
queue goes back to being what it's for: a line for the twelve thrones.

## What it registers

- **The lots.** `/power lots` — Zeus divides the world *blind*. He doesn't pick the sky;
  he draws it, and then spends the rest of the mythology insisting that proves something.
- **Olympus.** `/power olympus` above y=150. Build it first, found it second.
- **The Twelve.** Athena, Apollo, Artemis, Hermes, Ares, Hephaestus, Dionysus and
  Aphrodite — *claimed*, gated on **essence**. Which means the players who fought the
  Titanomachy as sworn soldiers, died, went back to the queue and watched an age turn are
  the ones who can afford a throne. Time served in the story buys a bigger part in the
  next one. That is the payoff for the entire spirit system.
- **Prayer.** A mortal stands on gold, under open sky, and says `/power pray zeus help me`.
  The god is told, wherever they are. `/power boon` answers it. **Not answering is also an
  answer**, and the Chronicle records both. Favor accrues on both sides — and favor is what
  the Trojan War gets fought with.
- **`/power decree`** — Zeus forbids the gods to intervene for three minutes. Implemented
  by cancelling `PowerUseEvent`, so it applies to powers from addons *written after this
  one*. The Iliad's "Zeus decides each morning who is allowed to help" turns out to be
  already implemented, three chapters early, by accident.

## What it reaches into

**Aphrodite is a callback to a different jar.** She rose from the foam where the blood of
Uranus fell into the sea — which happened in EraOfCreation. If that addon isn't installed,
the sky was never cut, and she simply cannot be claimed. Nothing crashes. A story that
wasn't told leaves a hole exactly its own shape.

**The Titans who fought get bound.** Kronos, Hyperion, Coeus, Crius and Iapetus belong to
EraOfCreation. On era-advance this addon calls `roles.extend(...)` to flip them to
`Endurance.ERA`, and the engine's retirement pass — which runs immediately after — dissolves
them back into the spirit world with an epithet and a pocket of essence. Bound in Tartarus;
back in the queue, rich, first in line for a throne. The neutrals (Oceanus, Themis,
Mnemosyne) keep their names, because Zeus was lenient with them and so are we.

**The three brothers get the powers of ruling** — which Titanomachy had no reason to give
them, because in that story they were children hiding in a cave.

## The hole it opens in itself

The Twelve were never a fixed list; the Greeks couldn't agree on it either. So:

```kotlin
mythos.extensions.contribute(
    OlympianSeat.POINT,
    OlympianSeat("persephone", "Persephone", listOf("spring", "the dead"),
                 "Six seeds. Six months. Nobody asked you.", powers = listOf("bloom")),
)
```

The seat becomes a claimable role *and* an optional beat of this era. Load order doesn't
matter. To contribute one that carries behaviour:
`compileOnly("net.crewco:olympian-order:0.1.0")` + `depends: [ OlympianOrder ]`.
