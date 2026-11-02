# Lock-End datapack core/init. Called once when the world (re)loads. Selector
# note: MC 26.3 uses the "level" option for dimension filters.

# The master record of the lock is the fake-player score "#locked" on the
# "lockend_state" objective; scoreboard data persists with the world save,
# so a persisted lock re-applies automatically after every restart.
scoreboard objectives add lockend_state dummy

# First boot (or a wiped scoreboard): default to unlocked. A full-range
# bounds interval like this checks existence only, not the value.
execute unless score #locked lockend_state matches -2147483648..2147483647 run scoreboard players set #locked lockend_state 0

# Warnings about a missing spawn anchor may fire again on every boot.
scoreboard players set #spawnwarned lockend_state 0
