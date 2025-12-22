# BOCRacingV2

**Project**: BOCRacingV2  
**Purpose**: Iterative, ground-up rewrite of BOCRacePlugin  
**Status**: Active development – exploratory + refactor-driven  

BOCRacingV2 is a clean rewrite of a **working production plugin**.
This project is intentionally iterative: we study what already works,
identify pain points (UX, redundancy, maintainability), and redesign
systems one piece at a time.

This is not a blind rewrite and not a forced migration.
It is an informed rebuild.

---

## 📁 Project Layout

- **BOCRacingV2 (this repo)**  
  `D:\Projects\BOCRacingV2`  
  → New implementation, clean architecture, evolving design

- **BOCRacePlugin (v1 – reference)**  
  `D:\Projects\BOCRacePlugin`  
  → Working plugin used for behavioral understanding and comparison

The v1 plugin is available as **reference material**:
- to understand how features behave
- to review implementation ideas
- to identify redundancy and UX issues

We do **not** blindly copy code, but we do actively study it.

---

## 🔗 Repositories

- **BOCRacingV2 (active)**  
  https://github.com/AbnVet/BOCRacingV2

- **BOCRacePlugin (legacy reference)**  
  https://github.com/AbnVet/BOCRacePlugin  
  *(Used for behavior, patterns, and lessons learned)*

---

## 🧭 How This Project Is Worked On

This project is built through **conversation + review + iteration**:

1. Existing v1 behavior is reviewed
2. UX or architectural issues are identified
3. Improvements are discussed and decided
4. Cursor is instructed to implement *specific, scoped changes*
5. Code is reviewed again

Cursor has access to **both codebases** and may:
- analyze v1 code
- suggest improvements
- compare patterns
- highlight redundancy

Final decisions are always made deliberately before implementation.

---

## 🧠 Design Philosophy (Flexible by Design)

- No hard commitment to legacy patterns
- No forced separation unless it proves useful
- Course-driven logic preferred, but revisited if needed
- Database-first approach preferred, but schema may evolve
- Admin UX clarity is a top priority

If a decision turns out to be wrong, we change it.
This rewrite exists *because* flexibility was missing before.

---

## 🛠 Build & Verify

```bash
cd D:\Projects\BOCRacingV2
D:\Maven\bin\mvn -q package


📚 Research & Development References

These are living references, not fixed rules.

Paper / Bukkit Development

Paper Dev Docs: https://docs.papermc.io/paper/dev/

Paper Javadocs: https://jd.papermc.io/paper/

Event Listeners: https://docs.papermc.io/paper/dev/event-listeners/

Scheduler: https://docs.papermc.io/paper/dev/scheduler/

Persistent Data Containers: https://docs.papermc.io/paper/dev/pdc/

Movement & Detection

PlayerMoveEvent: https://jd.papermc.io/paper/org/bukkit/event/player/PlayerMoveEvent.html

Vehicle Events: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/vehicle/

Elytra Glide: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/entity/EntityToggleGlideEvent.html

Ray Tracing: https://jd.papermc.io/paper/org/bukkit/util/RayTraceResult.html

Database & Storage

SQLite (JDBC): https://www.sqlite.org/index.html

HikariCP: https://github.com/brettwooldridge/HikariCP

MySQL Pooling: https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration