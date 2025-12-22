# BOCRacingV2 - Quick Reference

**Project**: BOCRacingV2 - Clean rewrite of BOCRacePlugin  
**Status**: Fresh start - building from ground up  

---

## 📁 Project Locations

- **New Plugin Location**: `D:\Projects\BOCRacingV2`
- **Maven Location**: `D:\Maven`
- **Old Plugin Reference**: `D:\Projects\BOCRacePlugin` (working v1 - for behavioral reference only)

---

## 🔗 Repository Links

- **GitHub**: https://github.com/AbnVet/BOCRacingV2.git
- **Old Plugin Reference**: `D:\Projects\BOCRacePlugin` (branch: `RacePerameters`, tag: `v1-prod-stable`)

---

## 🎯 Core Goals (Keep in Mind)

1. **Database-First**: SQLite default, MySQL optional - NO YAML record storage
2. **Unified Course Model**: Single `Course` class for boat & air races
3. **Mode Derivation**: SOLO/MULTIPLAYER from spawn count (1 = SOLO, 2+ = MULTIPLAYER)
4. **Clean Architecture**: No compatibility shims, no deprecated patterns
5. **Feature Parity**: All v1 functionality must work

---

## 🛠️ Build & Run

```bash
cd D:\Projects\BOCRacingV2
D:\Maven\bin\mvn clean package
```



---

## 📚 Reference the Old Plugin

**Location**: `D:\Projects\BOCRacePlugin`

**Use for**:
- Understanding HOW features work (behavioral reference)
- Understanding domain requirements
- **DO NOT**: Copy-paste code - implement fresh and clean

**Key Files to Reference**:
- `src/main/java/com/bocrace/model/Course.java` - Boat course structure
- `src/main/java/com/bocrace/model/AirRaceCourse.java` - Air course structure
- `src/main/java/com/bocrace/storage/` - Storage patterns (understand, don't copy)
- `docs/DATA_SCHEMA.md` - Data requirements

---

## 💡 Remember

- **Fresh rewrite** - don't migrate, implement cleanly
- **Database for records** - SQLite/MySQL only
- **Unified models** - no duplication
- **Clean code** - no hacks, no technical debt
- **Reference old plugin** for behavior understanding only

---

## 📖 Development References

Use these references while writing BOCRacingV2 (Paper 1.21.10+, Java 21):

### Paper Development Documentation
- **Paper Dev Docs**: https://docs.papermc.io/paper/dev/
- **Events/Listeners**: https://docs.papermc.io/paper/dev/event-listeners/
- **Scheduler** (use repeating task for active racers): https://docs.papermc.io/paper/dev/scheduler/
- **PDC** (store powerup state safely): https://docs.papermc.io/paper/dev/pdc/
- **Adventure UI** (bossbar/titles): https://docs.papermc.io/adventure/ and https://docs.papermc.io/adventure/bossbar/
- **Paper Javadocs** (search events/classes): https://jd.papermc.io/paper/

### Boat & Movement Events
- **VehicleExitEvent** (DQ on leaving boat): https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/vehicle/VehicleExitEvent.html
- **PlayerMoveEvent** details: https://jd.papermc.io/paper/org/bukkit/event/player/PlayerMoveEvent.html
- **MoveEvent performance discussion**: https://www.spigotmc.org/threads/playermoveevent-vs-scheduler-repeatingtask-which-would-cause-the-least-lag.260576/

### Elytra (Air Races)
- **EntityToggleGlideEvent**: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/entity/EntityToggleGlideEvent.html
- **Elytra system patterns** (open source): https://github.com/bruno-medeiros1/elytra-essentials

### Ray Trace Helpers (Optional for pickup/trigger detection)
- **RayTraceResult**: https://jd.papermc.io/paper/1.21.6/org/bukkit/util/RayTraceResult.html
- **World rayTrace methods**: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/util/class-use/RayTraceResult.html

### Database
- **HikariCP**: https://github.com/brettwooldridge/HikariCP
- **Hikari MySQL config**: https://github.com/brettwooldridge/HikariCP/wiki/MYSQL-Configuration
- **Practical pooling tutorial**: https://www.spigotmc.org/threads/tutorial-implement-mysql-in-your-plugin-with-pooling.61678/

---

**Last Updated**: 2025-01-XX
