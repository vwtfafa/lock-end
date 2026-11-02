# Fires while the lock is active but no spawn anchor exists yet. Warned once
# per boot so the chat does not flood.
execute if score #spawnwarned lockend_state matches 0 run tellraw @a {"text":"Lock-End: no spawn anchor is set! An operator must run /function lockend:set_spawn once at the desired overworld location, otherwise players cannot be moved out of the locked End.","color":"red"}
scoreboard players set #spawnwarned lockend_state 1
