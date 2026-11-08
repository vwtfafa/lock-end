# Locks the End, broadcasts the change and evacuates the players already
# inside. The scoreboard command requires operator permissions, which is why
# the whole function is op-gated in vanilla.
scoreboard players set #locked lockend_state 1
tellraw @a {"text":"The End is now locked. Players inside the End are being moved out.","color":"yellow"}
function lockend:evacuate
