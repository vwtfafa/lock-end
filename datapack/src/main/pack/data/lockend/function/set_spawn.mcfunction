# Operator setup tool: call it once while standing in the overworld at the
# location players should be teleported to when they are locked out of the
# End (typically at world spawn). Places a persistent marker entity there;
# the previous overworld anchor is removed first. Non-overworld calls are
# rejected so evacuation (which only looks in the overworld) cannot miss it.
execute unless dimension minecraft:overworld run tellraw @s {"text":"Lock-End: stand in the overworld when placing the spawn anchor.","color":"red"}
execute unless dimension minecraft:overworld run return 1
execute in minecraft:overworld run kill @e[type=minecraft:marker,tag=lockend_spawn]
execute at @s run summon minecraft:marker ~ ~ ~ {Tags:["lockend_spawn"]}
scoreboard players set #spawnwarned lockend_state 0
tellraw @s {"text":"Lock-End: spawn anchor placed at your current position. Players locked out of the End now arrive here.","color":"green"}
