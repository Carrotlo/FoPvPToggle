![FoPvPToggle cover](docs/assets/cover.png)

# FoPvPToggle

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fopvptoggle) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fopvptoggle/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Personal PvP toggles with world, region, and damage controls.

*Developed by Carrotio.*

## Features

- Lets players control their personal PvP state.
- Applies fast damage protection and configurable toggle rules.
- Supports disabled worlds and WorldGuard forced-PvP regions.
- Includes optional PvE and FoBoss protection behavior.
- Provides administrator editing and reload controls.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fopvptoggle). Check the [versions page](https://modrinth.com/plugin/fopvptoggle/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `WorldGuard`, `PlaceholderAPI`

## Screenshots

![Configure PvP toggle behavior.](docs/assets/screenshots/fopvptoggle-admin-editor.png)

*Configure PvP toggle behavior.*

![Manage disabled worlds.](docs/assets/screenshots/fopvptoggle-disabled-worlds.png)

*Manage disabled worlds.*

![Manage forced-PvP regions.](docs/assets/screenshots/fopvptoggle-forced-pvp-regions.png)

*Manage forced-PvP regions.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fopvptoggle` — Toggle your PvP state Aliases: `/pvp`, `/pvptoggle`.
- `/fopvptoggleadmin <version|reload|editor>` — FoPvPToggle admin command Aliases: `/pvptoggleadmin`.

### Permissions

- `fopvptoggle.pvptoggle` — Toggle personal PvP state Default: `op`.
- `fopvptoggle.admin` — Access FoPvPToggle admin tools Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

When PlaceholderAPI is installed, `%fopvptoggle_status%` returns `enabled` or `disabled`.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `default-pvp-enabled`, `pvp-override`, `disabled-worlds`, `pve-protection`, `foboss-protection`, `worldguard`, `file-logging`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

FoPvPToggle 1.8 provides `me.foesio.foPvPToggle.api.PvpToggleApi` through Bukkit's
`ServicesManager`. The API is available after FoPvPToggle enables and is removed
on disable. Existing `FoPvPToggle` main-class boolean methods remain available for
older integrations, including FoBoss.

### Consumer setup

Compile against the FoPvPToggle jar as a provided dependency. Do not shade its API
into a consumer. If the jar is only local, install that exact built jar into your
local Maven repository first:

```sh
mvn install:install-file -Dfile=target/FoPvPToggle-1.8.jar -DgroupId=me.foesio -DartifactId=FoPvPToggle -Dversion=1.8 -Dpackaging=jar
```

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoPvPToggle</artifactId>
  <version>1.8</version>
  <scope>provided</scope>
</dependency>
```

For a `plugin.yml` consumer, add `softdepend: [FoPvPToggle]` when integration is
optional, or `depend: [FoPvPToggle]` when it is required. Optional consumers
should load their FoPvPToggle integration class only after confirming that the
provider is enabled; otherwise the consumer can fail class loading when the
provider is absent. Reacquire the service after an enable/disable cycle.

```java
if (Bukkit.getPluginManager().isPluginEnabled("FoPvPToggle")) {
    var registration = Bukkit.getServicesManager().load(PvpToggleApi.class);
    if (registration != null && registration.isPvpEnabled(player)) {
        // FoPvPToggle currently treats this online player as PvP-enabled.
    }
}
```

### Queries

- `isPvpEnabled(Player)` returns the online player's effective state, including
  global override, disabled worlds, cached forced-region state, and active
  temporary forces. It does not promise that Minecraft or another plugin will
  allow damage.
- `isPvpAllowedByToggle(Player, Player)` returns FoPvPToggle's pairwise damage
  decision for two online players. It follows the same rule as the plugin's
  damage listener, including its disabled-world handling. Other protections
  may still cancel damage.
- `isPveProtected(Player)` returns whether FoPvPToggle's optional mob protection
  currently applies. Temporary PvP force removes this protection while active.
- `storedPreference(UUID)` returns the explicit in-memory Boolean choice, or
  `Optional.empty()` if the configured default applies. Writes are asynchronous;
  this is not a database durability confirmation. It does not include temporary
  forces, worlds, regions, or the global override.

### Change requests and events

`requestPvpChange(Player, boolean)` attempts a lasting choice change. The player
must be online and have `fopvptoggle.pvptoggle`; override, disabled worlds,
forced regions, and active temporary forces may deny a request. The return value
is `PvpChangeResult`: `CHANGED`, `UNCHANGED`, `CANCELLED`, or a specific denial.
`UNCHANGED` means an explicit stored choice already equals the request. Setting
the configured default explicitly for the first time returns `CHANGED` because
it creates a stored choice.

`PlayerPvpChangeRequestEvent` is cancellable. It fires after validation but
before a voluntary command or API change. It does not fire for blocked or
unchanged requests. A listener can cancel without partial state or persistence
effects. It contains the player, old explicit choice (possibly empty), old
resolved preference, requested value, and `COMMAND` or `API` cause.

`PlayerPvpPreferenceChangedEvent` fires after the in-memory preference changes
and its database write is queued. It is not cancellable and does not confirm
that the write completed. It carries the old explicit choice, old resolved
preference, new explicit choice, and cause. A forced WorldGuard region can
change the stored choice with `REGION_ENFORCEMENT`; that change cannot be vetoed
through the request event. Loading state on startup and denied requests do not
fire it. The current region behavior persists `true` on entry and does not
restore the previous choice on exit.

```java
@EventHandler
public void onPvpRequest(PlayerPvpChangeRequestEvent event) {
    if (!event.getRequestedPreference() && isCombatTagged(event.getPlayer())) {
        event.setCancelled(true);
    }
}
```

### Temporary PvP force

`forcePvp(player, ownerPlugin, durationTicks)` returns an opaque lease UUID.
Duration must be 1 to 1,728,000 ticks (24 hours at 20 TPS). Multiple leases
stack. While any lease exists, the player is effectively PvP-enabled and cannot
voluntarily disable PvP. The stored choice is untouched. A lease expires at its
scheduled tick or ends when its owner disables, the player quits, or FoPvPToggle
disables. The owning plugin can end it early with
`releasePvpForce(ownerPlugin, leaseId)`. A foreign or missing lease returns
`false`. No preference event fires when a lease starts or ends.

### Threading and compatibility

Call player and action methods on the Bukkit primary server thread. Off-thread
change and lease requests throw `IllegalStateException`; Player and WorldGuard
queries must not be called asynchronously. Events are dispatched inline on the
thread of the change, with Bukkit's asynchronous flag matching that dispatch
context. Do not retain a `Player` from an event for off-thread use. The plugin's
current metadata does not declare Folia support. Public API signatures use only
plugin-owned and Bukkit/Java types, not relocated FoPluginCore types.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `PvpToggleApi`

```java
boolean isPvpEnabled(Player);
boolean isPvpAllowedByToggle(Player, Player);
boolean isPveProtected(Player);
Optional<Boolean> storedPreference(UUID);
PvpChangeResult requestPvpChange(Player, boolean);
UUID forcePvp(Player, Plugin, long);
boolean releasePvpForce(Plugin, UUID);
```

**Other public contract types:** `PvpChangeCause`, `PvpChangeResult`.

### Complete Bukkit event index

- `PlayerPvpChangeRequestEvent`.
- `PlayerPvpPreferenceChangedEvent`.

</details>
