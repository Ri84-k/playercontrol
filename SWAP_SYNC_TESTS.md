# PlayerControl 26.2 swap synchronization (sync.1)

Experimental. Back up the world and test with disposable fake players before using valuable inventories.

## Changes
- Restore broad save data first, then explicit fields, and teleport last.
- Split pair restoration from client refresh; skip fake-client synthetic respawn and avoid redundant cross-dimension respawn.
- Reset movement baselines and update chunk tracking.
- Restore fractional XP and copy statistics instead of retaining live references.
- Log stable entity UUID, profile, dimension, coordinates, rotation, occupied slots, total item count and XP at swap/release.
- Extend dedicated-server smoke test with live mutation by entity UUID.

## Manual validation
1. Spawn a disposable bot, stop any existing Carpet action, swap, walk 10 blocks, change hotbar items and XP, release and swap again. Compare [swap-sync] release before A and release after B. Position, item counts and XP should match (entity UUIDs should differ).
2. Repeat with a bot in another dimension. Confirm no client rubber-banding, ghost entities or repeated moved-wrongly warnings.
3. Repeat for armor/offhand, survival abilities, and inventory GUI, then check appearance on release. Mounts/sleep and advanced Carpet/GCA state require separate tests.
4. Check normal manual and force release, disconnect, bot death, and server stop on a disposable save.
5. If the log shows correct server state immediately after release but a later reset, investigate fake-player action packs, GCA and later ticks. Action packs are not transferred in this revision.

The dedicated-server test exercises fake-to-fake state restoration, not a real client's movement packets or singleplayer rendering. No runtime result is asserted until CI/manual tests pass.
