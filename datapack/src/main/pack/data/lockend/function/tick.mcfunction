# Runs on every server tick while the world is loaded. Keeps the guard as a
# no-op when the End is unlocked.
execute if score #locked lockend_state matches 1.. run function lockend:evacuate
