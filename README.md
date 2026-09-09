# Football

Football is a team-based football minigame plugin developed for Minecraft servers.

Players are divided into Red and Blue teams, enter the arena, and try to score goals using a physics-based ball system.

The plugin includes arena management, ball physics, goal detection, overtime, scoreboard support, TAB isolation, team armor, statistics, rewards, join signs, PlaceholderAPI support, and custom football textures.

---

## Features

* Red and Blue team system
* Multi-arena support
* Physics-based football ball
* Base64 Player Head ball texture support
* Custom Model Data support
* Custom goal regions
* Own-goal protection
* Configurable match duration
* Configurable goal limit
* Overtime system
* Goal celebration effects
* Arena scoreboard
* Arena-specific TAB system
* TAB player isolation
* Arena-only chat
* Team-colored leather armor
* Team-colored name tags
* Player inventory restoration
* Join signs
* Winner rewards
* Draw rewards
* Player statistics
* Top 10 leaderboards
* PlaceholderAPI support
* Legacy and HEX color support
* Fully configurable match settings

---

## Gameplay

Football has two teams:

* Red Team
* Blue Team

When a player joins an arena, the plugin automatically checks team sizes.

If one team has fewer players, the player is assigned to that team. If both teams have the same number of players, a team is selected randomly.

The match starts when the required minimum number of players is reached.

Default minimum players:

```text
2
```

Once the countdown finishes, the ball is spawned at the configured center position and the match begins.

The objective is simple:

Score more goals than the opposing team before the match ends.

---

## Match System

Default match settings:

```yaml
match:
  start-delay-seconds: 10
  duration-seconds: 300
  goal-limit: 5
  default-min-players: 2
  default-max-players: 14
  goal-reset-delay-ticks: 40
```

By default:

* Match duration: 5 minutes
* Goal limit: 5
* Minimum players: 2
* Maximum players: 14

A match can also end early if a team reaches the configured goal limit.

---

## Overtime

If the match ends in a draw, Football can automatically start overtime.

Example:

```text
Red 4 - 4 Blue
```

Configuration:

```yaml
match:
  overtime:
    enabled: true
    duration-seconds: 300
    ignore-goal-limit: true
```

If the score is still tied when overtime ends, the match is completed as a draw.

---

## Ball System

Football uses a custom ball movement system.

Ball movement can be affected by:

* Player direction
* Hit direction
* Horizontal force
* Vertical force
* Gravity
* Air drag
* Ground friction
* Bounce factor
* Maximum speed

Example:

```yaml
ball:
  physics:
    normal-hit-force: 0.85
    shift-hit-force: 1.05
    upward-force: 0.62
    gravity: 0.045
    air-drag: 0.985
    ground-friction: 0.90
    bounce-factor: 0.55
    max-speed: 2.40
    contact-radius: 1.35
    hit-cooldown-ticks: 6
    reset-distance: 80.0
```

---

## Custom Ball Texture

Football supports custom Player Head textures using Base64 texture values.

Example:

```yaml
ball:
  material: SLIME_BLOCK
  head-value: "BASE64_TEXTURE_VALUE"
  custom-model-data: 0
  transform: FIXED
```

When `head-value` is not empty, the ball is rendered as a Player Head using the supplied texture.

If `head-value` is empty, the normal material and Custom Model Data system is used.

---

## Multi-Arena Support

You can create multiple independent football arenas.

Each arena can have its own:

* Red team spawn
* Blue team spawn
* Ball spawn
* Red goal
* Blue goal
* Minimum players
* Maximum players
* Join sign

Arena data is stored in:

```text
plugins/Football/arenas.yml
```

---

## Commands

Main commands:

```text
/football
/fb
```

Player commands:

```text
/fb join <arena>
/fb leave
/fb spectate <arena>
/fb top
```

Administrator commands:

```text
/fb create <arena>
/fb delete <arena>
/fb team <arena> <red/blue> spawn
/fb ballspawn <arena>
/fb setgoal <arena> <red/blue>
/fb sign <arena>
/fb minplayers <arena> <amount>
/fb maxplayers <arena> <amount>
/fb reload
```

---

## Permissions

```text
football.admin
football.join
football.spectate
football.top
```

Defaults:

| Permission          | Default  |
| ------------------- | -------- |
| `football.admin`    | OP       |
| `football.join`     | Everyone |
| `football.spectate` | Everyone |
| `football.top`      | Everyone |

---

## PlaceholderAPI

Football includes PlaceholderAPI support.

Player statistics:

```text
%football_wins%
%football_losses%
%football_goals%
%football_played%
```

Top goals:

```text
%football_top_goals_1_name%
%football_top_goals_1_value%
```

Ranks from 1 to 10 are supported.

Top wins:

```text
%football_top_wins_1_name%
%football_top_wins_1_value%
```

Ranks from 1 to 10 are supported.

---

## Player Statistics

Football stores player statistics by UUID.

Tracked values include:

* Wins
* Losses
* Goals
* Matches played

Statistics are stored in:

```text
plugins/Football/stats.yml
```

Players can view the leaderboard with:

```text
/fb top
```

---

## Rewards

Winner and draw rewards can be configured using console commands.

Example:

```yaml
rewards:
  winner:
    enabled: true
    commands:
      - "give %player% emerald 5"

  draw:
    enabled: true
    commands:
      - "give %player% emerald 2"
```

Supported reward placeholders include:

```text
%player%
%uuid%
%arena%
%team%
%red%
%blue%
```

This allows Football to work with economy, points, crate, item, and other command-based plugins.

---

## Scoreboard

Football provides an arena scoreboard system.

It can display:

* Arena name
* Red score
* Blue score
* Remaining time
* Match phase
* Match state
* Player team
* Player count
* Spectator count

The update interval can be configured.

```yaml
scoreboard:
  enabled: true
  update-ticks: 20
```

---

## TAB System

Football can isolate the TAB list for arena players.

Example:

```yaml
tab:
  enabled: true
  arena-only-players: true
  show-spectators: true
  force-refresh-ticks: 5
```

Players inside an arena can be limited to seeing only players from the same arena.

If the TAB plugin is installed, Football can integrate with its API.

---

## Arena Chat

Football includes optional arena-only chat.

```yaml
chat:
  enabled: true
  isolate-global-chat: true
```

Players and spectators inside an arena can communicate without receiving messages from unrelated players outside the arena.

---

## Team Armor

Football can temporarily equip players with team-colored leather armor.

```yaml
team-armor:
  enabled: true
  lock-inventory: true
  red-color: "#FF3B3B"
  blue-color: "#3B6CFF"
```

The player's original armor and inventory are restored when leaving the arena.

---

## Name Tags

Players can receive team-colored name tags.

Example:

```text
[RED] PlayerName
[BLUE] PlayerName
```

The format can be customized from the configuration.

---

## Join Signs

A join sign can be linked to an arena.

Create one with:

```text
/fb sign <arena>
```

Example sign format:

```text
[Football]
arena
1/14
Waiting
```

Players can click the sign to join the arena.

---

## Requirements

* Java 21
* Paper or Spigot
* Minecraft 1.21+
* PlaceholderAPI

Optional:

* TAB

---

## Building From Source

Football uses Maven.

Build the project with:

```bash
mvn clean package
```

The compiled plugin will be created inside:

```text
target/
```

---

## Configuration

Most Football systems can be configured through `config.yml`.

This includes:

* Match duration
* Goal limit
* Minimum players
* Maximum players
* Overtime
* Ball physics
* Ball textures
* Scoreboard
* TAB
* Chat
* Team names
* Team colors
* Team armor
* Name tags
* Rewards
* Join signs
* Messages
* Goal effects

After editing the configuration, use:

```text
/fb reload
```

---

## License

Check the repository license before redistributing, modifying, or publishing the plugin.

---

## Support

If you encounter a problem, include the following information when reporting it:

```text
Minecraft version
Paper/Spigot version
Java version
Football version
Relevant console error
Steps to reproduce the problem
```
