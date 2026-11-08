# Tells the calling source whether the End is locked. Usable by any
# operator (and by the console via /function lockend:status).
execute if score #locked lockend_state matches 1.. run tellraw @s {"text":"The End is LOCKED.","color":"red"}
execute unless score #locked lockend_state matches 1.. run tellraw @s {"text":"The End is unlocked.","color":"green"}
