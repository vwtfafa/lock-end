# Unlocks the End and broadcasts the change.
scoreboard players set #locked lockend_state 0
tellraw @a {"text":"The End has been unlocked.","color":"green"}
