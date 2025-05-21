# RankProxyPlugin

A Velocity proxy plugin for managing and tracking player ranking points across Minecraft servers using a MySQL database.  
Points are awarded, set, and retrieved via in-game commands and scheduled tasks.

---

## 💡 Features

- ✅ Add, set, or query points for any online player
- ✅ Automatically add configurable points (interval + amount) to all non-staff players
- ✅ Exclude staff members from automatic and manual point assignment
- ✅ Manage `stafflist` via in-game commands (`/staffadd`, `/staffremove`, `/stafflist`)
- ✅ Loads credentials and settings from YAML config file (`resources.yaml`)
- ✅ Built-in `/reloadconfig` command to reload configuration without restarting
- ✅ Integrates with external **RankPointsAPI** from [Samhuwsluz/RankPointsAPI](https://github.com/Samhuwsluz/RankPointsAPI)

---

## ⚙️ Commands

| Command | Description | Permission |
|--------|-------------|------------|
| `/addpoints <player> <amount>` | Adds (or subtracts) points from a player (not allowed for staff) | `rankproxyplugin.addpoints` |
| `/setpoints <player> <amount>` | Sets a player's points to an exact value (not allowed for staff) | `rankproxyplugin.setpoints` |
| `/getpoints <player>` | Displays a player's current points | `rankproxyplugin.getpoints` |
| `/reloadconfig` | Reloads the YAML configuration | `rankproxyplugin.reloadconfig` |
| `/staffadd <player>` | Adds a player to the staff exclusion list | `rankproxyplugin.staff.add` |
| `/staffremove <player>` | Removes a player from the staff exclusion list | `rankproxyplugin.staff.remove` |
| `/stafflist` | Displays the current list of staff members | `rankproxyplugin.staff.list` |

---

## 📁 Configuration

The plugin expects a `resources.yaml` file at:

```
src/main/resources/resources.yaml
```

It is auto-generated if missing. Example:
```yaml
mysql:
  host: "your-host.com:3306"
  user: "your_mysql_user"
  password: "your_mysql_password"

debug: true

autopoints:
  interval: 60    # in seconds
  amount: 1       # points to give each tick
```

---

## 🛠 Setup

### Prerequisites

- Java 21
- Maven 3.8+
- Velocity Proxy (tested with 3.1.1+)
- MySQL-compatible database

### Build

```bash
mvn clean package
```

The shaded JAR will be located at:
```
target/rankproxyplugin-1.0-shaded.jar
```

---

## 🔌 Installation

1. Place the JAR file into your `plugins/` folder of the **Velocity proxy**
2. Start the server once to generate `resources.yaml`
3. Configure the database credentials and settings inside the YAML
4. Restart the proxy

---

## 🧩 Dependencies

This plugin uses the following libraries:

- [Velocity API](https://velocitypowered.com/)
- [Configurate (YAML)](https://github.com/SpongePowered/Configurate)
- [RankPointsAPI (via JitPack)](https://github.com/Samhuwsluz/RankPointsAPI)

---

## 👤 Author

Timy Liniger  
[github.com/timyliniger](https://github.com/timyliniger)

---

## 📜 License

MIT License (or insert your preferred license)