# KlassenSMP

Ein vollstaendiges SMP-Plugin fuer den Minecraft-Server einer Schulklasse.
Java- **und** Bedrock-Spieler, deutsche Texte, alles ueber Konfigurationsdateien
einstellbar.

Gebaut ausschliesslich gegen die **Spigot/Bukkit-API** – es werden keine
Paper-Klassen, keine Paper-Events und keine NMS-Zugriffe verwendet. Das Plugin
laeuft damit auf jedem normalen Spigot-Server (auf Paper ebenfalls, aber Paper
ist keine Voraussetzung).

---

## Inhalt

- [Voraussetzungen](#voraussetzungen)
- [Installation](#installation)
- [Funktionen](#funktionen)
- [Befehle](#befehle)
- [Permissions](#permissions)
- [Konfiguration](#konfiguration)
- [Datenbank](#datenbank)
- [Java und Bedrock](#java-und-bedrock)
- [Performance](#performance)
- [Bauen](#bauen)
- [Bekannte Einschraenkungen](#bekannte-einschraenkungen)
- [Fehlerbehebung](#fehlerbehebung)

---

## Voraussetzungen

| | |
|---|---|
| Minecraft | Java Edition 1.21.x |
| Server | Spigot oder Bukkit (Paper funktioniert, ist aber nicht noetig) |
| Java | 21 oder neuer |
| Build | Maven 3.8+ |
| Datenbank | SQLite (Standard, keine Einrichtung noetig) oder MySQL/MariaDB |

**Optionale Plugins.** Fehlt eines davon, laeuft KlassenSMP ganz normal weiter –
nur die jeweilige Zusatzfunktion ist dann aus:

| Plugin | Wofuer |
|---|---|
| [Floodgate](https://geysermc.org/) | Zuverlaessige Bedrock-Erkennung fuer Tablist und Scoreboard |
| [Geyser](https://geysermc.org/) | Bedrock-Spieler koennen ueberhaupt verbinden |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | KlassenSMP meldet seine Economy als Vault-Anbieter an |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Fremde Platzhalter in Tablist, Scoreboard und Chat |

Diese Plugins sind **keine** Compile-Abhaengigkeit. Sie werden zur Laufzeit
per Reflection angebunden, damit die JAR klein bleibt und ohne zusaetzliche
Maven-Repositories gebaut werden kann.

---

## Fertige Datei herunterladen

Die gebaute Plugin-Datei liegt direkt im Repository und muss **nicht** selbst
gebaut werden:

**[`dist/Klassen-SMP-Plugin.jar`](../../raw/claude/klassensmp-minecraft-plugin-dlnylk/dist/Klassen-SMP-Plugin.jar)**

Auf GitHub im Ordner `dist/` auf die Datei klicken und dann auf
**Download** (Pfeil-Symbol) gehen. Die Datei wird bei jeder Aenderung am
Quellcode automatisch neu gebaut und dort aktualisiert.

Alternativ liegt sie nach jedem Build auch unter
*Actions -> Build -> Artifacts -> KlassenSMP*.

---

## Installation

1. `Klassen-SMP-Plugin.jar` nach `plugins/` kopieren.
2. Server starten. Beim ersten Start entstehen:

   ```
   plugins/KlassenSMP/
   ├── config.yml          Hauptkonfiguration
   ├── messages.yml        Alle Texte (deutsch)
   ├── kits.yml            Kits
   ├── crates.yml          Crates und Belohnungen
   ├── quests.yml          Taegliche/woechentliche Aufgaben
   ├── achievements.yml    Erfolge
   ├── events.yml          Server-Events
   ├── spawn.yml           Serverspawn
   ├── database.db         SQLite-Datenbank
   ├── moderation.log      Protokoll aller Moderationsaktionen
   └── backups/            Backups
   ```

3. Spawn setzen: `/setspawn`
4. Ränge zuweisen (Beispiel mit LuckPerms):

   ```
   /lp group default permission set klassensmp.rank.spieler true
   /lp group vip     permission set klassensmp.rank.vip true
   /lp group mod     permission set klassensmp.rank.moderator true
   /lp group admin   permission set klassensmp.rank.admin true
   ```

5. Nach Aenderungen an den Dateien: `/smpadmin reload`

> **Hinweis zu den JDBC-Treibern:** Sie sind nicht in der JAR enthalten,
> sondern werden ueber den `libraries:`-Eintrag der `plugin.yml` beim ersten
> Start von Maven Central geladen (Spigot-Funktion ab 1.16.5). Der Server
> braucht dafuer einmalig eine Internetverbindung.

---

## Funktionen

**Spieler**
Profil pro UUID, Spielzeit, Statistiken (Kills, Tode, Mobs, abgebaute und
platzierte Bloecke, verdientes/ausgegebenes Geld), Homes, TPA, Spawn, Warps,
PvP-Schalter, Graeber.

**Wirtschaft**
Bargeld und Bankkonto, `/pay` mit Mindestbetrag und Obergrenze, `/baltop` als
GUI oder Text, Adminbefehle, optionale Vault-Registrierung.

**Fortschritt**
Erfolge mit Belohnung, Titel und Broadcast; taegliche und woechentliche
Aufgaben, die pro Spieler zufaellig – aber reproduzierbar – aus einem Pool
gezogen werden; Kits mit Cooldown, Preis und Permission; Crates mit
gewichteten Belohnungen und Ziehungsanimation.

**Schutz**
Eigenes Claim-System (rechteckig, volle Weltenhoehe, chunk-indiziert),
Spawnschutz, geschuetzte Welten, Explosions- und Kolbenschutz, Schutz von
Kisten, Tueren, Redstone, Tieren und Villagern.

**Moderation**
Ban, Tempban, Mute, Tempmute, Warn mit automatischen Folgemassnahmen, Kick,
Freeze, Vanish, Invsee, Endersee, SocialSpy, Teamchat, Moderationsmodus mit
eigenem Inventar und Moderations-GUI. Jede Aktion landet in `moderation.log`.

**Schutzsysteme**
Anti-Spam (Cooldown, Flood, Wiederholungen, Grossbuchstaben), Command-Limit,
Anti-Bot-Join-Schutz mit zeitlich begrenztem Schutzmodus. Es wird **nie**
automatisch gebannt, und es werden **keine IP-Adressen gespeichert oder
protokolliert** – lediglich ein Hash zur Zaehlung im aktuellen Zeitfenster.

**Server**
TPS-Ueberwachung, Performance-GUI, Server-Booster in drei Stufen,
Item-/Mob-Bereinigung, Lag-Warnungen mit Cooldown, Backups, Admin-GUI,
Weltuebersicht, Event-System (Spleef, PvP-Turnier, Drop, Mob-Arena, Parkour,
Schatzsuche, Bauwettbewerb).

---

## Befehle

Alle Befehle besitzen Tab-Vervollstaendigung. Admin-Optionen werden Spielern
ohne Berechtigung nicht vorgeschlagen.

### Spieler

| Befehl | Aliase | Beschreibung |
|---|---|---|
| `/smp [info\|hilfe]` | `/klassensmp` | Uebersicht und Serverinformationen |
| `/spawn` | | Zum Serverspawn |
| `/home [name]` | `/h` | Zu einem Home |
| `/sethome [name]` | | Home setzen |
| `/delhome <name>` | | Home loeschen |
| `/homes` | | Eigene Homes auflisten |
| `/tpa <Spieler>` | | Teleportanfrage senden |
| `/tpahere <Spieler>` | | Spieler zu sich bitten |
| `/tpaccept [Spieler]` | `/tpja` | Anfrage annehmen |
| `/tpdeny [Spieler]` | `/tpnein` | Anfrage ablehnen |
| `/warp [name]` | | Warp-Liste als GUI oder direkt teleportieren |
| `/money [Spieler]` | `/geld`, `/balance`, `/bal` | Guthaben |
| `/pay <Spieler> <Betrag>` | `/zahlen` | Geld ueberweisen |
| `/baltop` | `/geldtop` | Reichste Spieler |
| `/bank <einzahlen\|abheben> <Betrag>` | | Bankkonto |
| `/stats [Spieler]` | `/statistik` | Statistiken |
| `/playtime [Spieler]` | `/spielzeit` | Spielzeit |
| `/achievements [Spieler]` | `/erfolge` | Erfolgsuebersicht |
| `/quests` | `/aufgaben` | Aufgaben und Belohnungen |
| `/kit [name]` | `/kits` | Kits |
| `/crate <oeffnen\|vorschau\|liste> [crate]` | `/kiste` | Crates |
| `/grave [tp <id>]` | `/grab` | Eigene Graeber |
| `/pvp` | | PvP ein-/ausschalten |
| `/claim <erstellen\|loeschen\|info\|liste\|vertrauen\|entfernen>` | `/grundstueck` | Grundstuecke |
| `/event <beitreten\|verlassen\|liste>` | `/events` | Events |
| `/msg <Spieler> <Text>` | `/w`, `/tell`, `/m`, `/fluester` | Private Nachricht |
| `/reply <Text>` | `/r`, `/antwort` | Antworten |
| `/ignore [Spieler]` | `/ignorieren` | Ignorieren |
| `/chat` | | Chat-Informationen |
| `/tps` | | Aktuelle TPS |

### Team

| Befehl | Beschreibung |
|---|---|
| `/mod` | Moderationsmodus (Vanish, Flug, kein Schaden, kein Hunger, Moderationsitems) |
| `/kick <Spieler> [Grund]` | Vom Server werfen |
| `/ban <Spieler> [Grund]` | Dauerhaft sperren |
| `/tempban <Spieler> <Dauer> [Grund]` | Zeitlich sperren (`10m`, `2h`, `7d`) |
| `/unban <Spieler>` | Sperre aufheben |
| `/mute`, `/tempmute`, `/unmute` | Stummschaltung |
| `/warn <Spieler> [Grund]` | Verwarnen |
| `/warnings [Spieler]` | Strafhistorie |
| `/freeze <Spieler>` | Spieler festhalten |
| `/vanish [Spieler]` | Unsichtbarkeit |
| `/invsee <Spieler>` | Inventar ansehen |
| `/endersee <Spieler>` | Enderchest ansehen |
| `/teleport <Spieler> [Ziel]` | Teleportieren |
| `/socialspy` | Private Nachrichten mitlesen |
| `/staffchat <Text>` (`/sc`) | Teamchat |
| `/mutechat` | Globalen Chat stummschalten |
| `/lag` | Ausfuehrliche Leistungsdaten |
| `/event <starten\|stoppen\|sieger>` | Events steuern |

### Administration

| Befehl | Beschreibung |
|---|---|
| `/smpadmin [reload]` (`/ksadmin`) | Admin-GUI bzw. Neuladen |
| `/setspawn` | Spawn setzen |
| `/setwarp <name> [permission] [material]` | Warp anlegen |
| `/delwarp <name>` | Warp loeschen |
| `/eco <give\|take\|set> <Spieler> <Betrag>` | Guthaben verwalten |
| `/kit <erstellen\|loeschen\|bearbeiten> <name>` | Kits aus dem eigenen Inventar |
| `/crate key <crate> <Spieler> [anzahl]` | Schluessel vergeben |
| `/performance` (`/perf`) | Performance-GUI |
| `/serverboost <normal\|performance\|extreme\|aufraeumen>` (`/boost`) | Server-Booster |
| `/backup [liste]` | Backup erstellen bzw. auflisten |

> `/teleport` und `/tp` gibt es auch in Vanilla. Sollte auf einem Server die
> Vanilla-Variante gewinnen, ist der Befehl von KlassenSMP immer als
> `/klassensmp:teleport` erreichbar.

---

## Permissions

Alles laeuft ueber Permissions – es gibt **keine fest codierten Rechte**.

| Sammelrecht | Enthaelt |
|---|---|
| `klassensmp.*` | Alles (Owner) |
| `klassensmp.admin` | Verwaltung, Backups, Booster, Warps, Economy-Admin, Bypass-Rechte |
| `klassensmp.mod` | Alle Moderationsrechte |
| `klassensmp.use` | Alle Spielerbefehle (Standard: `true`) |

Wichtige Einzelrechte:

```
klassensmp.spawn           klassensmp.home            klassensmp.home.unlimited
klassensmp.tpa             klassensmp.tpa.nocooldown  klassensmp.warp
klassensmp.economy         klassensmp.economy.pay     klassensmp.economy.admin
klassensmp.kit             klassensmp.kit.admin       klassensmp.kit.nocooldown
klassensmp.crate           klassensmp.crate.admin     klassensmp.claims
klassensmp.claims.bypass   klassensmp.claims.admin    klassensmp.protection.bypass
klassensmp.performance     klassensmp.serverboost     klassensmp.serverboost.extreme
klassensmp.backup          klassensmp.ban             klassensmp.mute
klassensmp.warn            klassensmp.freeze          klassensmp.vanish
klassensmp.invsee          klassensmp.endersee        klassensmp.socialspy
klassensmp.chat.color      klassensmp.antispam.bypass klassensmp.moderation.exempt
klassensmp.teleport.bypassdelay                       klassensmp.graves.others
```

Die vollstaendige Liste mit Beschreibungen steht in der `plugin.yml`.

**Ränge** werden ueber `klassensmp.rank.<id>` zugewiesen. Standardmaessig gibt
es `spieler`, `schueler`, `vip`, `moderator`, `admin` und `owner`. Prefix,
Farbe, Sortiergewicht und Home-Anzahl stehen in der `config.yml` unter
`ranks.list` und sind frei erweiterbar.

---

## Konfiguration

Alles Wichtige liegt in `config.yml`:

| Abschnitt | Inhalt |
|---|---|
| `database` | SQLite/MySQL, Cache-Groesse, Autosave-Intervall |
| `ranks` | Ränge, Prefixe, Farben, Home-Limits |
| `tablist` | Kopf-/Fusszeile, Namensformat, Aktualisierungsintervall |
| `scoreboard` | Titel und bis zu 15 Zeilen mit Platzhaltern |
| `chat` | Chatformat, private Nachrichten, Teamchat, SocialSpy |
| `economy` | Waehrung, Startguthaben, Ober-/Untergrenzen, Vault |
| `homes`, `teleport`, `tpa`, `spawn` | Teleportation und Verzoegerungen |
| `pvp` | PvP-Regeln, Combat-Tag, Combat-Log-Verhalten |
| `claims`, `protection`, `worlds` | Grundstuecke, Spawnschutz, Weltregeln |
| `graves` | Graeber, Lebensdauer, Blockart |
| `moderation` | Broadcasts, Logdatei, automatische Verwarnungsfolgen |
| `antispam`, `antibot` | Schwellenwerte der Schutzsysteme |
| `performance`, `serverboost` | Ueberwachung, Bereinigung, Boost-Modi |
| `backup` | Ordner, Anzahl, Kompression, automatische Backups |
| `sounds`, `particles`, `gui` | Sounds, Partikel, GUI-Aussehen |

Alle Texte stehen in `messages.yml`. Farbcodes: `&a`, `&c`, … sowie Hex-Farben
im Format `&#RRGGBB`. Eine leere Zeichenkette (`""`) unterdrueckt eine
Nachricht komplett.

### Platzhalter fuer Tablist und Scoreboard

```
%player% %displayname% %rank% %prefix% %suffix% %namecolor% %platform%
%money% %balance% %bank% %earned% %spent%
%online% %max% %java% %bedrock% %ping% %tps% %status% %statusicon%
%playtime% %kills% %deaths% %mobkills% %blocksbroken% %blocksplaced%
%homes% %claims% %achievements% %achievements_total% %pvp%
%world% %entities% %chunks% %time% %date% %server% %website%
```

Ist PlaceholderAPI installiert, werden zusaetzlich alle dortigen Platzhalter
aufgeloest.

---

## Datenbank

Standard ist **SQLite** (`plugins/KlassenSMP/database.db`) – keine Einrichtung
noetig. Fuer MySQL/MariaDB:

```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: klassensmp
    user: klassensmp
    password: "..."
    use-ssl: false
```

Alle Spielerdaten haengen an der **UUID**, nie am Namen. Der Name wird nur als
Suchindex mitgefuehrt und bei jedem Beitritt aktualisiert.

Saemtliche Datenbankzugriffe laufen ueber einen eigenen Thread. Der Main
Thread wartet nie auf die Datenbank – die einzige Ausnahme ist das
abschliessende Speichern beim Herunterfahren, damit keine Daten verloren
gehen. Es werden ausschliesslich `PreparedStatement`s verwendet.

Tabellen: `ks_players`, `ks_homes`, `ks_warps`, `ks_punishments`,
`ks_achievements`, `ks_quests`, `ks_kit_uses`, `ks_claims`,
`ks_claim_members`, `ks_ignores`, `ks_graves`.

---

## Java und Bedrock

Bedrock-Spieler werden ueber die **Floodgate-API** erkannt (per Reflection,
ohne Compile-Abhaengigkeit). Als Rueckfallebene dient der von Floodgate
konfigurierte Namens-Prefix.

Ist Floodgate nicht installiert, laeuft das Plugin normal weiter und behandelt
alle Spieler als Java-Spieler. Es gibt eine entsprechende Meldung im Log.

In der Tablist erscheinen Java- und Bedrock-Spieler mit unterschiedlichem
Symbol, dazu die Zaehler `Java: x` und `Bedrock: y` sowie `Online: x/y`.
Die Werte aktualisieren sich im Intervall aus `tablist.update-ticks`
(Standard: alle 2 Sekunden) – nicht jeden Tick.

---

## Performance

**Wichtig:** Ein Bukkit-Plugin kann die **FPS eines Clients nicht erhoehen**.
KlassenSMP optimiert ausschliesslich die *Serverleistung*.

Gemessen bzw. angezeigt werden nur tatsaechlich verfuegbare Werte:

| Wert | Quelle |
|---|---|
| TPS | Eigene Messung (Aufgabe alle 20 Ticks, Soll- gegen Ist-Zeit) |
| Server-TPS | `Server#getTPS()`, sofern die laufende Spigot-Version das anbietet – sonst als „nicht verfuegbar" ausgewiesen |
| Entities, Mobs, Items | `World#getEntities()` |
| Chunks | `World#getLoadedChunks()` |
| Hopper | `Chunk#getTileEntities()` (nur auf Anfrage, nicht dauerhaft) |
| Redstone/s, Hopper-Transfers/s | Eigene Zaehler aus `BlockRedstoneEvent` und `InventoryMoveItemEvent` |
| Arbeitsspeicher | `Runtime` |
| Ping | `Player#getPing()`, sofern vorhanden |

Es werden **keine Werte geschaetzt oder erfunden**. Was die API nicht liefert,
wird als nicht verfuegbar markiert.

Massnahmen bei Last (alle einzeln abschaltbar):

- Herumliegende Items und XP-Kugeln entfernen – **nur** aelter als
  `item-lifetime-seconds`, nie benannte Items, nie Items aus der
  Schutzliste (`performance.cleanup.protected-items`), **niemals**
  Spielerinventare.
- Ueberzaehlige Mobs je Chunk entfernen – nie gezaehmte, benannte oder
  berittene Tiere.
- Warnung an alle mit `klassensmp.performance.alerts` – mit Cooldown, damit
  die Konsole nicht ueberlaeuft.

**Server-Booster** (`/serverboost`): `NORMAL`, `PERFORMANCE`, `EXTREME`.
`EXTREME` ist nie der Standard, verlangt ein eigenes Recht und in der GUI eine
Bestaetigung. Die Modi begrenzen Mob-Spawns, Hopper-Transfers, Redstone-
Aktivitaet und liegende Items je Chunk und reduzieren Plugin-Partikel.

Weitere Regeln im Code: keine Aufgaben jeden Tick, Tablist und Scoreboard mit
Intervall statt Tick, Scoreboard-Zeilen nur bei echter Aenderung, Claims ueber
einen Chunk-Index statt linearer Suche, Bewegungs-Events verwerfen reine
Kopfbewegungen sofort, alle Aufgaben werden beim Deaktivieren gestoppt.

---

## Bauen

```bash
git clone https://github.com/LennardOwnTest123006/Klassen-SMP-Plugin.git
cd Klassen-SMP-Plugin
mvn clean package
```

Ergebnis: `target/KlassenSMP-1.0.0.jar`

Das ist nur noetig, wenn du am Quellcode etwas aenderst. Fuer den normalen
Betrieb reicht die fertige Datei aus `dist/`.

Die einzige Compile-Abhaengigkeit ist `org.spigotmc:spigot-api` (Scope
`provided`) aus dem Spigot-Repository. Eine andere Spigot-Version laesst sich
ohne Codeaenderung waehlen:

```bash
mvn clean package -Dspigot.version=1.21.8-R0.1-SNAPSHOT
```

Bei jedem Push baut zusaetzlich der GitHub-Actions-Workflow
`.github/workflows/build.yml` das Projekt und legt die fertige JAR als
Artefakt „KlassenSMP" ab.

---

## Bekannte Einschraenkungen

- **FPS des Clients** lassen sich serverseitig nicht beeinflussen. Der
  „Server-Booster" optimiert die Serverleistung, nicht die Bildrate.
- **Server-TPS und Ping** stellt nicht jede Spigot-Version bereit. Fehlen sie,
  zeigt KlassenSMP die eigene TPS-Messung bzw. `-` statt eines erfundenen Werts.
- **MSPT und Tick-Zeiten** werden von der Spigot-API nicht angeboten und
  deshalb bewusst nicht angezeigt.
- **PlaceholderAPI**: KlassenSMP *nutzt* fremde Platzhalter, stellt aber keine
  eigene Expansion bereit. Eine Expansion muesste von `PlaceholderExpansion`
  erben, was eine Compile-Abhaengigkeit erzwingen wuerde. Die eigenen
  Platzhalter (`%money%`, `%tps%`, …) funktionieren in Tablist, Scoreboard und
  Chat ohne PlaceholderAPI.
- **Vault-Banken**: KlassenSMP kennt persoenliche Bankkonten, aber keine
  benannten Vault-Banken. `hasBankSupport()` meldet daher `false`.
- **Claims** sind rechteckig und gelten ueber die volle Weltenhoehe. Fuer ein
  Klassen-SMP reicht das; wer mehr braucht, deaktiviert `claims.enabled` und
  nutzt ein spezialisiertes Plugin.
- **Freeze** ist absichtlich fluechtig: nach einem Neustart ist niemand mehr
  eingefroren.
- **Bauwettbewerb**: Der Sieger wird vom Team mit `/event sieger <Spieler>`
  bestimmt – eine automatische Bewertung ist nicht moeglich.
- **JDBC-Treiber** kommen ueber `libraries:` aus Maven Central. Ein Server
  ganz ohne Internetzugang braucht die Treiber vorab im Server-Cache.

---

## Fehlerbehebung

**„Datenbankverbindung fehlgeschlagen"**
Bei SQLite: Schreibrechte auf `plugins/KlassenSMP/` pruefen. Bei MySQL:
Zugangsdaten und Erreichbarkeit pruefen. Der Treiber wird beim ersten Start
heruntergeladen – ohne Internetzugang schlaegt das fehl.

**„Floodgate nicht gefunden – Bedrock-Erkennung ist deaktiviert"**
Nur ein Hinweis. Ohne Floodgate gelten alle Spieler als Java-Spieler.

**Scoreboard oder Tablist bleiben leer**
`scoreboard.enabled` bzw. `tablist.enabled` pruefen. Laeuft ein zweites
Plugin auf demselben Scoreboard (z. B. ein anderes Tab-Plugin), kann es zu
Konflikten kommen – dann eines von beiden abschalten.

**Ränge werden nicht angezeigt**
Die Permission aus `ranks.list.<rang>.permission` muss gesetzt sein. Kontrolle
mit `/lp user <Spieler> permission check klassensmp.rank.vip`.

**Ein Befehl reagiert nicht**
Falls ein anderes Plugin denselben Namen belegt, ist der KlassenSMP-Befehl
immer unter `/klassensmp:<befehl>` erreichbar.

**Spieler koennen in fremden Gebieten nicht bauen**
`/claim info` zeigt den Besitzer. Baurecht erteilt der Besitzer mit
`/claim vertrauen <Spieler>`.

**Lag trotz Booster**
`/performance` oeffnen und die auffaelligen Bereiche ansehen. Meist sind es
grosse Mob- oder Item-Farmen. `/serverboost performance` begrenzt Hopper und
Redstone; `/serverboost aufraeumen` entfernt sofort herumliegende Objekte.

---

## Lizenz

Erstellt fuer den privaten Klassen-Server. Nutzung und Anpassung frei.
