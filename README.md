# FraMCit

**Turn any image or GIF URL into a Minecraft map you can hang on an item frame.**
Animated GIFs only animate for players who are actually looking at the frame.

[![Paper](https://img.shields.io/badge/Paper-26.3-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

🇪🇸 **[Léeme en español](README.es.md)**

---

## What it does

A player runs `/imagen <url>` and gets a map item in hand showing that image. Hang it on an
item frame and it stays there — through restarts, indefinitely.

If the image is an animated GIF or WEBP, it animates **only while a player is looking
directly at the frame**. A GIF nobody is looking at costs zero bandwidth and zero CPU. Each
player sees their own animation at their own pace.

No NMS. No reflection into server internals. Pure Paper API.

---

## Download

**[⬇ Download the latest FraMCit-1.1.0.jar](https://github.com/Dasannn/FraMCit/releases/latest/download/FraMCit-1.1.0.jar)**

Latest stable release: **v1.1.0**. Its updater release asset is signed with Ed25519.

All releases: [github.com/Dasannn/FraMCit/releases](https://github.com/Dasannn/FraMCit/releases)

### Requirements

| | |
|---|---|
| Server | **Paper 26.3** (not Spigot, not Bukkit) |
| Java | **25** (required by Paper 26.1+) |
| Dependencies | **None.** Vault and LuckPerms are optional |

FraMCit works on a bare server with no other plugins installed. That is a design rule, not
a coincidence — see [Working without Vault](#working-without-vault).

### Install

1. Stop the server.
2. Drop `FraMCit-1.1.0.jar` into `plugins/`.
3. Start the server.
4. `/imagen https://example.com/cat.gif`

> ⚠️ **Never replace the jar while the server is running.** Paper loads classes lazily, so
> overwriting a live jar makes classes that were not loaded yet disappear mid-session and
> you get `NoClassDefFoundError`. Stop first, then copy.

By default the command is **disabled for everyone**, including operators. Grant `framcit.imagen` explicitly; see [Permissions](#permissions).

---

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/imagen <url>` | `framcit.imagen` | Download the image and give you the map |
| `/imagen mias [page]` | `framcit.imagen` | List your images, five per page, with quota usage |
| `/imagen obtener <id>` | `framcit.imagen` | Retrieve a map you already own without using another slot |
| `/imagen borrar <id> confirmar` | `framcit.imagen` | Delete one of your images, freeing a slot |
| `/img update check` | `framcit.admin` | Check the latest stable GitHub release |
| `/img update` | `framcit.admin` | Stage the update for the next server restart |
| `/imagen recargar` | `framcit.admin` | Reload `config.yml` |
| `/imagen info` | `framcit.admin` | While looking at a frame: who made it, when, from what URL |
| `/imagen listar [player] [page]` | `framcit.admin` | List stored images, optionally filtered by player |
| `/imagen uso [player]` | `framcit.admin` | Measured resource usage: disk, RAM, running animations |
| `/imagen purgar <url\|id>` | `framcit.admin` | Delete an image from cache and disk |
| `/imagen purgar-jugador <player> confirmar` | `framcit.admin` | Delete everything a player owns |

Aliases: `/img`, `/image`. All player-facing messages are in Spanish and live in
`messages.yml`, so you can translate them without recompiling. The image list and delete
preview also support `&` color codes.

Both destructive commands require the literal word `confirmar`. Without it they only show
you what *would* happen.

`/img update` verifies the official GitHub release asset and its pinned Ed25519 signature,
then places it in Paper's plugin update folder under the current JAR name. Restart the server
to apply it; do not use `/reload` or replace a running plugin JAR. See
[`docs/release-signing.md`](docs/release-signing.md) for release-key setup.

---

## Permissions

FraMCit declares its nodes and asks about them. **Who gets what is your permission
manager's job, never the plugin's.**

| Node | Default | Grants |
|---|---|---|
| `framcit.imagen` | `false` | Use `/imagen`, `mias`, `obtener`, `borrar` |
| `framcit.admin` | `false` | All admin subcommands; also implies `framcit.imagen` |
| `framcit.gratis` | `false` | Exempt from payment; price is never even mentioned |
| `framcit.slots.1` | `false` | Own up to 1 image at a time |
| `framcit.slots.2` | `false` | Up to 2 images |
| `framcit.slots.3` | `false` | Up to 3 images |
| `framcit.slots.ilimitado` | `op` | No image limit |
| `framcit.frames.10` | `false` | Images of up to 10 frames |
| `framcit.frames.25` | `false` | Up to 25 frames |
| `framcit.frames.50` | `false` | Up to 50 frames |
| `framcit.frames.100` | `false` | Up to 100 frames |

Operators do not receive `framcit.imagen` or `framcit.admin` automatically. Grant them
explicitly (for example, with LuckPerms):

```text
/lp group default permission set framcit.imagen true world=world
/lp group moderator permission set framcit.admin true world=world
```

Use the world's actual name in the context (`world`, `world_nether`, `world_the_end`, or a
Multiverse world name). Repeat the grant for each world where the permission should apply.
You can also grant a player a permission in one world with
`/lp user <player> permission set framcit.imagen true world=world`.

### The two quotas

They measure different things and are independent:

- **Slots** limit *how many images* a player accumulates.
- **Frames** limit *how much memory each one costs*. Every map frame is exactly 16384
  bytes, so capping frames caps RAM and disk directly and predictably: 100 frames = 1.6 MB,
  20 frames = 320 KB.

A player can have 3 slots but only 10 frames per image.

### Three rules that surprise people

**1. Tiers do not stack — the highest wins.** A player with `framcit.slots.1` *and*
`framcit.slots.3` has **3**, not 4. They are steps on a ladder, not points to add up. This
means removing a rank never produces a weird intermediate number.

**2. No permission node can raise the server's hard ceiling.** A player's effective frame
cap is `min(their node, imagen.frames-maximos from config.yml)`. Granting
`framcit.frames.500` does **not** let them upload 500 frames — the server ceiling still
applies. That limit is decompression-bomb defence, not a preference, so there is
deliberately no "unlimited frames" node.

**3. A slot is occupied by an image you *own*, not by a frame or an item.** Take the map
off the wall, put it in a chest, hang it somewhere else — same slot, nothing changes. You
free a slot with `/imagen borrar`, and only that.

### Owning vs. creating

Two players who request the same URL share one stored image and one map id — that is the
cache working. But they are **two owners**, and it costs each of them a slot. Requesting a
URL somebody else already fetched downloads nothing, yet still uses your quota.

When an owner deletes it, nothing happens to the others: their maps keep working. The file
is only erased from disk when the **last** owner lets go.

---

## LuckPerms setup

LuckPerms is optional. `framcit.imagen` and `framcit.admin` default to `false`; operators do
not receive them automatically. Grant the permissions explicitly as shown above.

LuckPerms contexts can scope these grants to a world by its actual world name, for example:
`/lp group default permission set framcit.imagen true world=survival`.
These grants restrict command use only; maps already created continue to display and animate
normally in every world.

### A worked example

```
lp creategroup vip
lp creategroup mvp

lp group vip parent add default
lp group mvp parent add vip

lp group default permission set framcit.imagen true
lp group default permission set framcit.slots.1 true
lp group default permission set framcit.frames.10 true

lp group vip permission set framcit.slots.2 true
lp group vip permission set framcit.frames.25 true

lp group mvp permission set framcit.slots.3 true
lp group mvp permission set framcit.frames.50 true
lp group mvp permission set framcit.gratis true

lp group vip setweight 10
lp group mvp setweight 20
```

Assign a rank:

```
lp user Steve parent add vip
```

Give one person a one-off allowance without touching groups:

```
lp user Steve permission set framcit.slots.3 true
```

Check what actually applies:

```
lp user Steve info
lp user Steve permission check framcit.slots.2
lp editor
```

### Nodes vs. config defaults

With **no node at all**, `config.yml` decides: `slots.por-defecto` (1) and
`frames.por-defecto` (100). That is why the example grants `framcit.frames.10` to the
`default` group — otherwise a rankless player could upload 100-frame GIFs, because the
factory value restricts nothing.

You have two ways to do the same thing. Pick one:

- **Tighten `config.yml`** (`frames.por-defecto: 10`) and use nodes only to *raise* the cap
  for people who pay. Less work, and it fails safe: a player with no rank is restricted by
  omission rather than by you remembering to grant a node.
- **Leave `config.yml` alone** and grant explicit nodes to every group, `default` included.

### Testing quotas as an operator

`framcit.slots.ilimitado` is `default: op`, so **as an operator you will never hit the slot
limit**. To test it, negate the node on yourself:

```
lp user Steve permission set framcit.slots.ilimitado false
```

An explicit negation beats the `default: op`. Undo it with `permission unset`.

---

## Economy (Vault)

FraMCit can charge in-game money per image. It is **off by default** and entirely optional.

```yaml
economia:
  precio-por-imagen: 0.0   # 0 = free
```

Set a price, run `/imagen recargar`, and charging starts — no restart, no recompile.

### How the charge works

- Balance is checked **before** downloading. No money, no download.
- The charge is attempted immediately before delivery, after the image has been saved.
- If the server stops during charging/delivery, the pending map is restored when you next
  join, without charging you again. To avoid losing purchases, a stop just before the charge
  completes may result in a free map.
- **There are no refunds.** Deleting an image later does not return its price.
- A cache hit costs the full price. The quota measures occupancy; the price measures use.
- `framcit.gratis` exempts a player, and they never even see the price mentioned.

### Working without Vault

This is a hard design rule: **no external dependency may ever be mandatory.**

FraMCit boots and works on a server with no Vault, no economy plugin and no permission
manager. Vault is declared with `required: false` and compiled against but never bundled,
and every access to its types sits behind three guards — plugin present, plugin enabled,
and a `Class.forName` check — isolated in one package-private class.

Three situations all mean the same thing: **images are free and the plugin does not
complain.**

1. Vault is not installed.
2. Vault is installed but no economy plugin registered with it.
3. Price is 0.

You get one informational line at startup telling you which case you are in:

```
[FraMCit] Cobro de imágenes desactivado: Vault no está instalado en el servidor. Las imágenes son gratis.
```

That is information, not a warning. Having no economy is a perfectly normal way to run
this plugin.

---

## Configuration

Full reference in [`plugin/src/main/resources/config.yml`](plugin/src/main/resources/config.yml),
which is commented line by line.

```yaml
descarga:
  tiempo-conexion-segundos: 5     # connect timeout
  tiempo-lectura-segundos: 15     # total read timeout
  tamano-maximo-mb: 5             # hard byte cap, enforced while streaming
  max-redirecciones: 3
  hilos: 4                        # download pool size        [restart required]
  capacidad-cola: 20              # queue capacity            [restart required]

imagen:
  dimension-maxima: 4096          # max source pixels per side
  frames-maximos: 100             # SERVER CEILING, no node exceeds it
  retardo-defecto-ms: 100
  retardo-minimo-ms: 100          # 100 ms = 10 fps cap

animacion:
  distancia-maxima-vision: 24.0   # blocks
  periodo-rastreo-ticks: 5        # gaze raytrace period      [restart required]
  pausa-por-mirada: 5             # minutes; 0 disables

cache:
  tamano-maximo-mb: 128           # LRU cache ceiling

economia:
  precio-por-imagen: 0.0

slots:
  por-defecto: 1
  maximo-nodo: 10

frames:
  por-defecto: 100
  maximo-nodo: 100
```

Most values apply instantly with `/imagen recargar`. The three marked `[restart required]`
do not, because changing a live thread pool or timer mid-flight could drop requests.

Any missing or invalid value falls back to its default **with a warning in the console**.
The plugin never becomes unusable because of a typo.

### Long-gaze pause

If a player stares at the same frame without looking away for longer than
`pausa-por-mirada`, their animation of that map stops.

- The counter is **continuous gaze**. Looking away resets it to zero, so a player who looks,
  leaves and comes back never triggers it.
- It is **per player, per frame**. Nobody else is affected.
- It freezes on the current frame — it does not reset to frame 0 and does not go blank.
- **Looking away and back is the only thing that resumes it.**

Someone playing normally never reaches five minutes of unbroken staring. Someone who does
is almost always AFK in front of a frame, where the animation is just burning bandwidth.

Note that pausing **does not free memory** — the image stays cached and resumes instantly.
The only thing that stops is sending packets to that player. If you want to cut RAM, the
lever is the frame quota, not this.

---

## Storage

```
plugins/FraMCit/
  config.yml
  messages.yml
  index.json          <- map id, url, author, owners, date, format, frames, delays
  images/
    <id>.fmc          <- frames already converted to map palette bytes
```

Images are stored **already converted** to map palette bytes, not as the original file.
Restarting the server is a plain byte read — no decoding, no conversion. It also guarantees
a hanging picture looks the same forever, even if the conversion algorithm changes later.

`index.json` is plain readable text you can inspect. `.fmc` is a short header (magic,
format version, palette version, frame count) followed by raw 16384-byte frames, written
atomically via a temp file and `ATOMIC_MOVE`.

**Uninstalling is safe.** Remove the jar and the maps simply go blank. No world data is
touched or corrupted.

---

## How the animation works

This is the central design decision, and it needs no NMS.

The Bukkit API provides exactly what is required:

- `MapRenderer.render(MapView, MapCanvas, Player)` receives the player, so a renderer marked
  **contextual** can paint something different for each player on the same map.
- `Player.sendMap(MapView)` renders and sends that map **to one player only**.

Put together: the plugin keeps a per-player frame index, a periodic raytrace decides who is
looking at which frame, and the scheduler advances and sends frames only to those players.
Nobody looking means nothing is sent.

The cost therefore scales with **number of viewers**, not number of hanging GIFs.

### Crafting is blocked

FraMCit maps are inert: they cannot be used in a crafting table, a cartography table, or a
Crafter block. The result slot comes out empty and your ingredients are never consumed.

The reason that matters is zooming. Enlarging a map produces a **new map with a different
id** that the plugin knows nothing about, so the player would end up holding a blank map
convinced the plugin is broken. Copying breaks nothing technically, but it hands out
pictures with no trail for staff to follow, so it goes in the same bucket.

The whole category is blocked rather than a list of recipes, so a future Minecraft recipe
that consumes maps cannot silently open a hole.

---

## Security

A command that makes your server fetch arbitrary URLs is an SSRF risk. These protections
exist regardless of who has permission to run it, and **none of them are configurable** —
you cannot disable in a file what is meant to protect the server.

- **`http` and `https` only.** No `file:`, `ftp:`, `jar:` or anything else.
- **Internal destinations blocked**: loopback, link-local, and private ranges (10/8,
  172.16/12, 192.168/16, CGNAT 100.64/10, IPv6 ULA and equivalents). The *resolved IP* is
  validated, not just the hostname text.
- **Every redirect hop is re-validated.** Redirects are followed manually, never
  automatically, so a redirect to `127.0.0.1` is caught.
- **Hard byte cap enforced while streaming**, ignoring whatever `Content-Length` claims.
- **Connect and read timeouts**, and a redirect limit.
- **Dimension and frame limits checked from the file header before decoding**, so a
  decompression bomb is rejected before it can allocate memory.

Every image creation is logged with player name, UUID, full URL, size, format and resulting
map id. That log is a requirement, not a feature: it is the only way staff can tell who
posted what.

FraMCit does **not** moderate content. Responsibility for what players post lies with the
players and your staff.

### Known limitation: DNS rebinding

Worth stating plainly rather than hiding.

`HttpClient` resolves the hostname **twice** — once when we validate the IP, once when it
connects. An attacker controlling DNS for their own domain can change the answer in between
and land the connection on an internal IP that validation never saw. This is the classic
bypass for this class of protection.

The JVM's 30-second DNS cache makes both lookups agree in practice, which **narrows** the
window and makes the attack hard — but it does not eliminate it, since every TTL expiry
opens a fresh one. Running the server with `-Dsun.net.inetaddr.ttl=0` reopens it fully, and
FraMCit warns loudly at startup if it detects that.

The three technical fixes were investigated and rejected as worse than the disease:
rewriting the URI to the IP breaks TLS certificate verification (a critical MITM hole),
`InetAddressResolverProvider` is JVM-global and would affect Paper and every other plugin,
and hand-rolling an HTTP client over `SSLSocket` means writing a manual HTTP/1.1 parser on
the most security-sensitive path in the plugin.

So the residual risk is **accepted, in writing**, and documented in
[`docs/constitution.md`](docs/constitution.md) §6.1.

---

## Limits

| Limit | Value |
|---|---|
| Max download size | 5 MB |
| Max GIF frames | 100 |
| Max frames per second | 10 |
| Max source dimensions | 4096 × 4096 |
| Connect timeout | 5 s |
| Total read timeout | 15 s |
| Redirects followed | 3 |
| Concurrent downloads (server-wide) | 4 |

Accepted formats: PNG, JPEG, BMP, GIF, WEBP.

**Animated WEBP plays at a fixed 10 fps.** The decoding library reads every frame but does
not expose per-frame delays. Since the plugin caps at 10 fps anyway, the difference is
imperceptible for most files.

One image is one map — 128×128. Multi-frame mosaics are out of scope.

---

## Building from source

```bash
git clone https://github.com/Dasannn/FraMCit.git
cd FraMCit/plugin
./gradlew shadowJar
```

Output: `plugin/build/libs/FraMCit-1.1.0.jar`. You need JDK 25; Gradle will fetch the
toolchain if you do not have it on `PATH`.

Run the tests with `./gradlew test` — there are 255 of them, concentrated where failure
would be silent: the URL guard, the byte cap, palette conversion, quota resolution,
ownership, and the gaze counter.

---

## How this project was built

This plugin was developed spec-first. Every rule in the code traces to a written decision,
and those documents are in [`docs/`](docs/):

| Document | What it holds |
|---|---|
| [`constitution.md`](docs/constitution.md) | Non-negotiable principles and the security floor |
| [`spec.md`](docs/spec.md) | Spec 001 — images, GIFs, animation, the map item |
| [`spec-002-economia.md`](docs/spec-002-economia.md) | Spec 002 — optional charging via Vault |
| [`spec-003-slots.md`](docs/spec-003-slots.md) | Spec 003 — per-player quotas |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Components, threads, storage, risk register |
| [`plan.md`](docs/plan.md) / [`tasks.md`](docs/tasks.md) | Phases and the task list |

The phase reports in `docs/` include corrections where an implementation report overstated
what had actually been verified. They were kept rather than cleaned up, because a record of
what is *measured* versus what is *projected* is worth more than a tidy one.

---

## Contributing

Issues and pull requests are welcome. Two things to know first:

1. **Read [`docs/constitution.md`](docs/constitution.md).** Some things are deliberately
   out of scope — keyword image search, multi-frame mosaics, content moderation, NMS, Folia
   support, multi-version support. A PR adding one of those will be declined regardless of
   quality.
2. **The security floor is not negotiable.** A PR that makes SSRF protections configurable,
   or that adds a permission node capable of raising a safety ceiling, will not be merged.

If you find a security issue, please open a private advisory rather than a public issue.

---

## License

[MIT](LICENSE) © 2026 Dasannn

You can use it, modify it, run it on a paid server, or fold it into something closed. Keep
the copyright notice and you are fine.
