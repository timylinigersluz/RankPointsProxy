# RankProxyPlugin

Ein **Velocity-Proxy-Plugin** für Minecraft, das **Spieler-Punkte, Ränge, Staff-Rollen** und neu auch **AFK-Status** zentral verwaltet.  
Es verbindet sich mit einer MySQL-Datenbank und sorgt dafür, dass alle Punkte, Ränge und AFK-Informationen **serverübergreifend synchronisiert** sind.

---

## ✨ Funktionen

- ✅ **Automatische Punktevergabe** (z. B. jede Minute 1 Punkt für Online-Zeit)
- ✅ **Keine Punkte im AFK-Modus**  
  → AFK-Status wird automatisch über EssentialsX erkannt und vom Proxy berücksichtigt
- ✅ **Punkte manuell vergeben oder setzen** über Befehle
- ✅ **Staff-Mitglieder zentral verwalten** (`/staffadd`, `/staffremove`, `/stafflist`)
- ✅ **Staff** kann von der automatischen Punktevergabe ausgeschlossen werden
- ✅ **LuckPerms-Integration**
    - Automatische Promotion/Demotion nach Punktestand
    - Staff-Spieler automatisch in Staff-Laufbahn
    - Normale Spieler in Standard-Laufbahn (`player`)
- ✅ **Clusterweiter AFK-Sync**
    - EssentialsX auf Paper-Servern sendet AFK-Status → Proxy vergibt keine Punkte
- ✅ **Konfiguration in YAML**
- ✅ **Reload ohne Neustart** (`/reloadconfig`)

---

## 🔧 Voraussetzungen

- Velocity Proxy **3.1.1+**
- Java **17 oder höher** (empfohlen: Java 21)
- MySQL-Datenbank
- [LuckPerms](https://luckperms.net) (auf dem Proxy)
- [EssentialsX](https://essentialsx.net) (auf Paper-Servern für AFK-Erkennung)
- [RankPointsAPI](https://github.com/timylinigersluz/RankPointsAPI) (Paper-seitige Dependency)

---

## 📦 Installation

1. Lade die **`RankProxyPlugin-x.x.jar`** herunter oder baue sie selbst:

   ```bash
   mvn clean package
   ```

2. Lege die JAR-Datei in den `plugins/`-Ordner des **Velocity-Proxys**.

3. Starte den Proxy einmal, damit die Konfigurationsdateien automatisch erzeugt werden:
    - `resources.yaml`
    - `ranks.yaml`

4. Passe die Dateien nach deinen Bedürfnissen an.

5. Stelle sicher, dass auf allen **Paper-Servern**:
    - **EssentialsX** installiert ist
    - **RankPointsAPI** läuft  
      (dieses sendet den AFK-Status automatisch über `rankproxy:afk`)

6. Starte alle Server neu.

---

## ⚙️ Konfiguration

### `resources.yaml`

```yaml
mysql:
  host: "jdbc:mysql://mc-mysql01.host.de:3306/mc_points"
  user: "mc_user"
  password: "geheimes_passwort"

log:
  level: DEBUG   # INFO, WARN oder ERROR

points:
  interval-seconds: 60          # Intervall für Punktevergabe
  amount: 1                     # Punkte pro Intervall
  promotion-interval-seconds: 60

storage:
  autosave-interval-seconds: 300  # OfflinePlayerStore speichern

staff:
  cache-ttl-seconds: 60
  give-points: false              # Staff bekommt keine Punkte
  group: "staff"                  # LuckPerms-Gruppe für Staff

default-group: "player"
```

---

### `ranks.yaml`

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

## ⚙️ AFK-System

| Komponente | Aufgabe |
|-------------|----------|
| **EssentialsX (Paper)** | Erkennt AFK-Spieler automatisch |
| **RankPointsAPI (Paper)** | Sendet AFK-Status (`uuid;true/false`) über `rankproxy:afk` |
| **RankProxyPlugin (Velocity)** | Empfängt AFK-Status und speichert ihn im `AfkManager` |
| **SchedulerManager** | Vergibt keine Punkte an AFK-Spieler |

**Ergebnis:**  
→ Spieler, die `/afk` eingeben oder länger inaktiv sind, **bekommen keine Punkte mehr**,  
bis sie wieder aktiv sind.

---

## 📜 Befehle

| Befehl | Beschreibung | Permission |
|--------|--------------|------------|
| `/addpoints <spieler> <anzahl>` | Punkte hinzufügen oder abziehen | `rankproxyplugin.addpoints` |
| `/setpoints <spieler> <anzahl>` | Punkte setzen | `rankproxyplugin.setpoints` |
| `/getpoints [spieler]` | Eigene oder fremde Punkte anzeigen | `rankproxyplugin.getpoints` |
| `/reloadconfig` | Konfiguration neu laden | `rankproxyplugin.reloadconfig` |
| `/staffadd <spieler>` | Spieler zu Staff hinzufügen | `rankproxyplugin.staff.add` |
| `/staffremove <spieler>` | Spieler aus Staff entfernen | `rankproxyplugin.staff.remove` |
| `/stafflist` | Staffliste anzeigen | `rankproxyplugin.staff.list` |
| `/rankinfo` | Aktuellen Rang und Fortschritt anzeigen | `rankproxyplugin.rankinfo` |

---

## 🔑 Permissions

```json
"permissions": {
  "rankproxyplugin.addpoints": { "description": "Erlaubt /addpoints" },
  "rankproxyplugin.setpoints": { "description": "Erlaubt /setpoints" },
  "rankproxyplugin.getpoints": { "description": "Erlaubt /getpoints" },
  "rankproxyplugin.reloadconfig": { "description": "Erlaubt /reloadconfig" },
  "rankproxyplugin.staff.add": { "description": "Erlaubt /staffadd" },
  "rankproxyplugin.staff.remove": { "description": "Erlaubt /staffremove" },
  "rankproxyplugin.staff.list": { "description": "Erlaubt /stafflist" },
  "rankproxyplugin.staffpoints": { "description": "Erlaubt Punktvergabe an Staff (wenn aktiviert)" }
}
```

---

## 💡 Use Cases

- 🎮 **Level-Progression:** Spieler steigen automatisch nach Punktestand auf.
- 👑 **Staff-Verwaltung:** Admins verwalten Staff zentral inkl. LuckPerms-Sync.
- ⚖️ **Fairness:** Staff und AFK-Spieler erhalten keine Punkte.
- 🔧 **Flexibilität:** Punktevergabe steuerbar, Reload ohne Neustart.

---

## 🙋‍♂️ Autor

- 🧑‍🏫 Timy Liniger (KSR Minecraft Projekt)
- 🌍 [ksrminecraft.ch](https://ksrminecraft.ch)

---

## 📄 Lizenz

Open Source – frei nutzbar & anpassbar.  
Bitte nenne die Quelle, wenn du das Plugin weiterverwendest.

---

### 📘 Changelog (neu)

| Version | Änderungen |
|----------|-------------|
| **1.1.0** | ✨ AFK-System integriert (EssentialsX → Proxy Sync) |
| **1.0.0** | Erstveröffentlichung mit Rank- und Staff-Verwaltung |
