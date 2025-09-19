# RankProxyPlugin

Ein Velocity-Proxy-Plugin für Minecraft, das Spieler-Punkte, Ränge und Staff-Rollen zentral verwaltet.  
Es verbindet sich mit einer MySQL-Datenbank und sorgt dafür, dass alle Punkte und Ränge serverübergreifend synchronisiert sind.

---

## ✨ Funktionen

- ✅ Punkte automatisch vergeben (z. B. jede Minute 1 Punkt für Online-Zeit)
- ✅ Punkte manuell vergeben oder setzen über Befehle
- ✅ Staff-Mitglieder zentral verwalten (`/staffadd`, `/staffremove`, `/stafflist`)
- ✅ Staff von automatischer Rangvergabe ausschließen (konfigurierbar)
- ✅ LuckPerms-Integration:
    - Automatische Promotion/Demotion nach Punktestand
    - Staff-Spieler werden automatisch in die Staff-Laufbahn gesetzt
    - Normale Spieler werden in die Standard-Laufbahn (`player`) gesetzt
- ✅ Alle Einstellungen über YAML-Konfigurationsdateien steuerbar
- ✅ Unterstützung für Reload (`/reloadconfig`) ohne Proxy-Neustart

---

## 🔧 Voraussetzungen

- Velocity Proxy Server **3.1.1+**
- Java **17 oder höher** (empfohlen: Java 21)
- MySQL-Datenbank
- [LuckPerms](https://luckperms.net) installiert auf dem Proxy

---

## 📦 Installation

1. Lade die `RankProxyPlugin-x.x.jar` herunter oder baue sie mit Maven:

   ```bash
   mvn clean package
   ```

2. Lege die JAR-Datei in den `plugins/`-Ordner des Velocity-Proxys.

3. Starte den Proxy einmal, damit die Konfigurationsdateien automatisch erzeugt werden:
    - `resources.yaml`
    - `ranks.yaml`

4. Passe die Dateien nach deinen Wünschen an (siehe unten).

5. Starte den Proxy neu.

---

## ⚙️ Konfiguration

### `resources.yaml`

Beispiel:
```yaml
mysql:
  host: "jdbc:mysql://mc-mysql01.host.de:3306/mc_points"
  user: "mc_user"
  password: "geheimes_passwort"

log:
  level: DEBUG   # INFO, WARN, ERROR möglich

points:
  interval-seconds: 60       # wie oft Punkte automatisch vergeben werden
  amount: 1                  # wie viele Punkte pro Intervall
  promotion-interval-seconds: 60  # wie oft Promotionen geprüft werden

storage:
  autosave-interval-seconds: 300  # OfflinePlayerStore speichern

staff:
  cache-ttl-seconds: 60
  give-points: false              # true = Staff bekommt Punkte, false = nicht
  group: "staff"                  # LuckPerms-Laufbahn für Staff

default-group: "player"           # LuckPerms-Laufbahn für normale Spieler
```

---

### `ranks.yaml`

Hier werden die Rangstufen definiert, die nach Punkten vergeben werden.

Beispiel:
```yaml
ranks:
  - name: "bronze"
    points: 10
    reward:
      - item: "minecraft:stone"
        amount: 5

  - name: "silver"
    points: 50
    reward:
      - item: "minecraft:iron_ingot"
        amount: 10
```

---

## 📜 Befehle

| Befehl | Beschreibung | Permission |
|--------|--------------|------------|
| `/addpoints <spieler> <anzahl>` | Punkte hinzufügen (oder abziehen) | `rankproxyplugin.addpoints` |
| `/setpoints <spieler> <anzahl>` | Punkte auf einen festen Wert setzen | `rankproxyplugin.setpoints` |
| `/getpoints [spieler]` | Eigene oder fremde Punkte anzeigen | `rankproxyplugin.getpoints` |
| `/reloadconfig` | Lädt die Konfiguration neu | `rankproxyplugin.reloadconfig` |
| `/staffadd <spieler>` | Spieler zur Staffliste hinzufügen und Staff-Gruppe zuweisen | `rankproxyplugin.staff.add` |
| `/staffremove <spieler>` | Spieler aus der Staffliste entfernen | `rankproxyplugin.staff.remove` |
| `/stafflist` | Alle Staff-Mitglieder anzeigen | `rankproxyplugin.staff.list` |
| `/rankinfo` | Zeigt den aktuellen Rang + Fortschritt an. Staff sieht seine LuckPerms-Gruppe. | `rankproxyplugin.rankinfo` |

---

## 🔑 Permissions

Im `velocity-plugin.json` definiert:

```json
"permissions": {
  "rankproxyplugin.addpoints": { "description": "Erlaubt /addpoints" },
  "rankproxyplugin.setpoints": { "description": "Erlaubt /setpoints" },
  "rankproxyplugin.getpoints": { "description": "Erlaubt /getpoints" },
  "rankproxyplugin.reloadconfig": { "description": "Erlaubt /reloadconfig" },
  "rankproxyplugin.staff.add": { "description": "Erlaubt /staffadd" },
  "rankproxyplugin.staff.remove": { "description": "Erlaubt /staffremove" },
  "rankproxyplugin.staff.list": { "description": "Erlaubt /stafflist" },
  "rankproxyplugin.staffpoints": { "description": "Erlaubt Punktvergabe an Staff-Mitglieder (zusätzlich muss staff.give-points=true sein)" }
}
```

---

## 💡 Use Cases

- 🎮 **Automatische Level-Progression**: Spieler sammeln Punkte für Online-Zeit → erreichen automatisch höhere Ränge (z. B. Bronze, Silber, Gold).
- 👑 **Staff-Verwaltung**: Lehrer/Admins können Staff zentral verwalten und automatisch in die richtige LuckPerms-Laufbahn setzen.
- ⚖️ **Faire Verteilung**: Staff kann von der Punktevergabe ausgeschlossen werden, damit nur Spieler an Wettbewerben teilnehmen.
- 🔧 **Flexible Steuerung**: Admins können Punkte manuell anpassen (z. B. Bonuspunkte für Events oder Tests).

---

## 🙋‍♂️ Autor

- 🧑‍🏫 Timy Liniger (KSR Minecraft Projekt)
- 🌍 [ksrminecraft.ch](https://ksrminecraft.ch)

---

## 📄 Lizenz

Open Source – frei nutzbar & anpassbar.  
Bitte nenne die Quelle, wenn du das Plugin weiterverwendest.
