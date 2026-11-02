# Evacuation core: runs every tick by "tick" while the lock is active and
# once immediately from "lock". Entity selectors cannot filter by dimension
# directly in MC 26.3 (no such option exists), so players are handed to
# "evacuate_player" one by one, filtered through the location check
# predicate "in_end".
execute as @a if predicate lockend:in_end run function lockend:evacuate_player
