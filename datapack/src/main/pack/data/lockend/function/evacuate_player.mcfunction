# Moves one player (context entity) out of the locked End to the anchor
# placed by "set_spawn" and tells them why. Tellraw only runs when the
# overworld anchor exists so a missing spawn cannot spam chat every tick
# (missing_spawn already throttles its own notice).
execute in minecraft:overworld unless entity @e[type=minecraft:marker,tag=lockend_spawn,limit=1] run function lockend:missing_spawn
execute in minecraft:overworld if entity @e[type=minecraft:marker,tag=lockend_spawn,limit=1] run tellraw @s {"text":"The End is locked. You are being moved out of the End.","color":"yellow"}
execute in minecraft:overworld if entity @e[type=minecraft:marker,tag=lockend_spawn,limit=1] at @e[type=minecraft:marker,tag=lockend_spawn,limit=1,sort=nearest] run tp @s ~ ~ ~
