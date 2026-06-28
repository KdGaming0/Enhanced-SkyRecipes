### 0.2.4

- Recipe data now downloads and is stored in a single tidy folder instead of being spread across several locations, and the large temporary download file is removed automatically once it has been processed.
- Smoother, more reliable startup: the mod no longer borrows Minecraft's shared background work queue. On slower (low-core) computers that sharing could contribute to the game hanging on the loading screen.
- The first time you launch this version your existing data is moved to the new folder automatically — nothing is re-downloaded.