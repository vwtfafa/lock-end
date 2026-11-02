# Operator setup tool: call it once while standing at the location players
# should be teleported to when they are locked out of the End (typically at
# the world spawn). Places a persistent marker entity there; the previous
# anchor is removed first.
kill @e[type=minecraft:marker,tag=lockend_spawn]
execute at @s run summon minecraft:marker ~ ~ ~ {Tags:["lockend_spawn"]}
scoreboard players set #spawnwarned lockend_state 0
tellraw @s {"text":"Lock-End: spawn anchor placed at your current position. Players locked out of the End now arrive here.","color":"green"}
