# OlympianOrder — Extending

## What it reaches into

**Zeus, Poseidon, Hades, Hera, Demeter, Hestia** all gain the powers of *ruling* — which
Titanomachy had no reason to give them, because in that story they were children hiding in a cave.

**The fighting Titans** (Kronos, Hyperion, Coeus, Crius, Iapetus — EraOfCreation's roles) are flipped to
`Endurance.ERA` at the moment this age begins, and the engine's retirement pass dissolves them into the
spirit world with an epithet and a pocket of essence. Bound in Tartarus; back in the queue, rich, first in
line for a throne. The neutrals keep their names, because Zeus was lenient with them.

## The hole it opens in itself

**`olympus:seats`** → contribute a `OlympianSeat`.

A god takes a throne. The Twelve were never a fixed list — the Greeks couldn't agree on it either. **ChthonicRealm uses this to seat Persephone, two addons later, and this jar needed no change at all.**

```kotlin
compileOnly("net.crewco:olympianorder:0.1.0")   // for the type
// addon.yml:  depends: [ OlympianOrder ]

mythos.extensions.contribute(Seats.POINT, ...)
```

**Load order does not matter.** `consume` replays every contribution already posted and receives
every one posted afterwards — so your jar may load before or after this one, and neither cares.
