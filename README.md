# Turnstile

A Velocity proxy plugin that locks down which backend servers players are allowed to switch to — like a real turnstile, it only lets people through who have the right access.

## What does this plugin do?

Turnstile adds two layers of protection around server switching (`/server`) on your Velocity network:

1. **Server prefix filter** — you configure a required prefix (e.g. `smp_`). Players can only ever connect to servers whose name starts with that prefix. Anything else is always blocked, no exceptions.
2. **Task permission gate** — you define named "tasks" (e.g. `building`) tied to a permission node. Any server whose name starts with a task's name (e.g. `building-1`, `building-2`) requires the player to hold that permission — otherwise the switch is denied.

On top of that, Turnstile replaces the `/server` command so that players only ever **see** servers they're actually allowed to join — blocked servers simply don't show up in the list or in tab-completion. There's no confusing "access denied" for a server a player didn't even know existed.

If you don't configure anything, Turnstile doesn't restrict anything — it's opt-in per rule.

## Why would I use this?

If your network has restricted or work-in-progress servers (build servers, staff-only areas, event servers, etc.) that should only be reachable by certain players, Turnstile gives you a simple, centrally managed way to enforce that — without needing to hand-configure permissions on every backend server individually, and without confusing players with servers they can't use.

## Commands

All commands require the `turnstile.admin` permission (server admins/staff only).

| Command | What it does |
|---|---|
| `/turnstile permission set <task> <permission>` | Require `<permission>` to join any server belonging to `<task>` |
| `/turnstile permission remove <task>` | Remove that requirement |
| `/turnstile permission list [page]` | Show all configured task → permission rules |
| `/turnstile server_prefix set <prefix>` | Require every server name to start with `<prefix>` |
| `/turnstile server_prefix show` | Show the currently configured prefix |

## Setup

Turnstile stores its settings in a PostgreSQL database so they survive restarts and can be managed live via commands (no server restarts needed to change rules). On first launch, it creates a small config file in its data folder where you fill in your database connection details, then everything else is managed in-game with `/turnstile`.

## Requirements

- A Velocity proxy
- A PostgreSQL database for Turnstile to store its settings in