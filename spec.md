# Turnstile – Spezifikation (Velocity Task-Gate Plugin)

**Autor:** tobifrosch
**Stack:** Java 25, Kotlin, Gradle (Kotlin DSL), Velocity API 4.0.0, PostgreSQL
**Maincommand:** `/turnstile`

Name-Idee: **Turnstile** – ein Drehkreuz lässt nur durch, wer den passenden Zugang hat (Task-Permission) und im richtigen Bereich unterwegs ist (Server-Prefix). Passt thematisch und ist kurz genug als Command.

---

## 1. Zielsetzung

Ein Velocity-Plugin, das Serverwechsel von Spielern zweifach absichert:

1. **Server-Prefix-Filter** (global): Zielserver muss mit einem konfigurierten Prefix beginnen (z. B. `smp_`), sonst wird der Wechsel **immer** abgebrochen – unabhängig von Tasks/Permissions.
2. **Task-Permission-Gate**: Für Server, die zu einer konfigurierten "Task" gehören (z. B. `building` → `building-1`, `building-2`), muss der Spieler eine zugewiesene Permission besitzen (`hasPermission()`), sonst wird der Wechsel abgebrochen.
3. **Server-Sichtbarkeit**: Server, die einen der obigen Checks nicht bestehen würden, tauchen für den jeweiligen Spieler gar nicht erst in `/server` (Liste + Tab-Completion) auf.

---

## 2. Begriffe

| Begriff | Bedeutung |
|---|---|
| Task | Logischer Servergruppen-Name ohne Suffix, z. B. `building` |
| Server | Konkrete Instanz, z. B. `building-1`, `building-2` |
| Server-Prefix | Globaler Pflicht-Prefix für JEDEN Zielserver, z. B. `smp_` |
| Permission-Node | String-Node, der per `player.hasPermission(node)` geprüft wird |

---

## 3. Ablauflogik (bei jedem Serverwechsel-Versuch)

Hook: `ServerPreConnectEvent` (deckt sowohl expliziten `/server`-Wechsel als auch initiale Verbindung über die Try-Liste ab).

Alle String-Vergleiche laufen **case-insensitive** (`.lowercase()` auf beiden Seiten).

```
1. Zielserver-Name ermitteln (lowercase)
2. PREFIX-CHECK (global, gilt für JEDEN Wechsel):
   - Kein Prefix konfiguriert → Check übersprungen (kein Lockout im Auslieferungszustand)
   - Prefix konfiguriert und Zielserver beginnt NICHT damit (lowercase-Vergleich) → ABBRUCH
3. TASK-PERMISSION-CHECK:
   - Passenden Task ermitteln: serverName.lowercase().startsWith(taskName.lowercase())
   - Kein passender Task konfiguriert → ERLAUBT (keine Regel = kein Zwang)
   - Passender Task gefunden, Spieler hat die Permission NICHT → ABBRUCH
   - Passender Task gefunden, Spieler hat Permission → ERLAUBT
4. Beide Checks bestanden → Wechsel erlaubt
```

Bei ABBRUCH: `event.setResult(ServerPreConnectEvent.ServerResult.denied())`, Spieler bleibt auf aktuellem Server. Die Denial-Nachricht läuft über die bestehende **utils-Messaging-Logik** (siehe Abschnitt 7), nicht über eigenen hartkodierten Text.

Hinweis zur Matching-Logik: `startsWith` ohne Pflicht-Trennzeichen bedeutet, `building` matcht auch `buildingXYZ` oder `building2`, nicht nur `building-1`/`building-2`. Bewusst so gewählt (deine Vorgabe).

---

## 4. Commands

Alle Subcommands erfordern die Permission `turnstile.admin`.

| Command | Wirkung |
|---|---|
| `/turnstile permission set <task_name> <permission_node>` | Legt Task-Permission-Paar an/überschreibt es |
| `/turnstile permission remove <task_name>` | Entfernt ein Task-Permission-Paar |
| `/turnstile permission list [page]` | Zeigt alle Task-Permission-Paare, **paginiert** (mehrseitig) |
| `/turnstile server_prefix set <prefix>` | Setzt den globalen Server-Prefix |
| `/turnstile server_prefix show` | Zeigt aktuell konfigurierten Prefix |

---

## 5. Server-Sichtbarkeit (`/server`-Liste & Tab-Completion)

Ziel: Server, die der Spieler laut Ablauflogik (Abschnitt 3) ohnehin nicht erreichen dürfte, sollen weder in der `/server`-Übersicht noch in der Tab-Completion auftauchen. **Das ist machbar**, erfordert aber eine Besonderheit:

- Velocitys eingebautes `/server`-Command wird deregistriert und durch eine eigene Implementierung ersetzt (Unregister + eigene Registrierung unter demselben Namen).
- Die eigene Implementierung filtert die Liste **pro ausführendem Spieler zur Laufzeit** – nicht global cachebar, da die Task-Permission-Prüfung von den individuellen Permissions des jeweiligen Spielers abhängt. Zwei Spieler sehen bei gleicher Serverlandschaft potenziell unterschiedliche Listen.
- Gleiche Filterlogik gilt für die Tab-Completion von `/server <partial>`.
- Basis der Filterung: exakt die Ablauflogik aus Abschnitt 3, nur ohne den eigentlichen Connect-Versuch (rein "würde erlaubt sein?"-Check).

---

## 6. Datenmodell (PostgreSQL)

```sql
CREATE TABLE IF NOT EXISTS task_permissions (
    task_name        TEXT PRIMARY KEY,
    permission_node   TEXT NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS plugin_settings (
    key    TEXT PRIMARY KEY,
    value  TEXT NOT NULL
);
-- server_prefix wird als Zeile ('server_prefix', 'smp_') in plugin_settings abgelegt
```

Schema-Erzeugung erfolgt automatisch beim Plugin-Start (`CREATE TABLE IF NOT EXISTS`). Task-Namen und Prefix werden so gespeichert, wie eingegeben (Original-Case) – der Lowercase-Vergleich passiert ausschließlich zur Laufzeit beim Check, nicht beim Speichern.

---

## 7. Architektur

- **In-Memory-Cache**: `ConcurrentHashMap<String, String>` für Task→Permission, `@Volatile String?` für den Prefix. Wird beim Proxy-Start einmalig aus Postgres geladen. Jede Config-Änderung über Commands schreibt sofort in den Cache (Write-Through) und asynchron in die DB – der heiße Pfad (`ServerPreConnectEvent`, `/server`-Filterung) greift **nie** direkt auf die DB zu.
- **DB-Zugriff**: HikariCP-Connection-Pool + JDBC-Treiber (`org.postgresql:postgresql`), beides als Shaded-Dependency ins Plugin-Jar gepackt (ShadowJar-Gradle-Plugin), da Velocity diese nicht mitbringt.
- **Async**: DB-Schreibvorgänge laufen über `proxyServer.getScheduler()` in einem eigenen Thread-Pool, nicht auf dem Netty-Event-Loop.
- **Commands**: Über Velocitys Brigadier-basierten `CommandManager` (native Tab-Completion für `task_name`/`permission_node`, Pagination für `permission list`).
- **Messaging-Integration**: Denial-Nachrichten (Abschnitt 3) und ggf. Command-Feedback sollen die bestehende Messaging-Logik aus `utils` übernehmen, statt eigenen hartkodierten Text zu verwenden. Konkrete Anbindung (Dependency auf `utils` vs. gleiches Format eigenständig nachbauen) ist noch offen und wird geklärt, sobald die relevante `utils`-Schnittstelle feststeht.

---

## 8. Konfiguration (Bootstrap)

Separate Config-Datei im Plugin-Datenverzeichnis (z. B. `config.toml`) für die DB-Verbindung – **nicht** über Commands, da Bootstrap-Only:

```toml
[database]
host = "localhost"
port = 5432
database = "velocity_taskgate"
user = "..."
password = "..."
```

---

## 9. Finalisierte Entscheidungen

1. **Matching-Logik**: einfaches `startsWith` (kein Trennzeichen-Zwang)
2. **Admin-Permission-Node**: `turnstile.admin`, fest verdrahtet
3. **Case-Insensitivity**: alle String-Vergleiche (Prefix- und Task-Matching) laufen über `.lowercase()`
4. **Denial-Messaging**: übernimmt die vorhandene `utils`-Messaging-Logik (Detail-Anbindung folgt später)
5. **`permission list`**: paginiert (mehrseitig)
6. **Server-Sichtbarkeit**: `/server`-Liste + Tab-Completion werden pro Spieler gefiltert (Abschnitt 5)

---

## 10. Implementierungs-Roadmap

1. **Projekt-Grundgerüst**: Gradle-Kotlin-DSL-Projekt, `velocity-api:4.0.0` (compileOnly), `velocity-plugin.json` (Name: Turnstile, Author: tobifrosch), ShadowJar-Plugin für Fat-Jar (Postgres-Treiber + HikariCP shaded).
2. **Config-Layer**: Bootstrap-Config laden/parsen (DB-Connection-Daten).
3. **DB-Layer**: HikariCP-Setup, Schema-Migration, Repository-Klassen (`TaskPermissionRepository`, `SettingsRepository`).
4. **Cache-Layer**: Laden beim Boot, Write-Through bei Änderungen, case-insensitive Lookups zur Laufzeit.
5. **Event-Listener**: `ServerPreConnectEvent`-Handler mit der Ablauflogik aus Abschnitt 3.
6. **`/server`-Override**: eingebautes Command deregistrieren, eigene gefilterte Implementierung (Liste + Tab-Completion) registrieren.
7. **Commands**: Brigadier-Commands (`permission set/remove/list`, `server_prefix set/show`) inkl. Permission-Gate, Tab-Completion, Pagination für `list`.
8. **Messaging-Integration**: Anbindung an `utils`-Messaging-Logik für Denial- und Command-Feedback.
9. **Fehlerbehandlung**: DB nicht erreichbar beim Boot → Plugin deaktiviert sich mit klarer Logmeldung; DB nicht erreichbar bei Command → Fehlermeldung an Ausführenden, Cache bleibt unverändert.
10. **Manuelle Testumgebung**: 2 Backend-Server (z. B. `smp_building-1`, `smp_building-2`) + 1 außerhalb des Prefix zum Testen von Abbruch und `/server`-Filterung.
11. **Packaging**: Jar-Name, Version, Author-Metadata (`tobifrosch`) im `velocity-plugin.json`.
