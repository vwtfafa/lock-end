# Modrinth – v4.0

## Summary

```
Lock the End globally — scheduled unlock, PlaceholderAPI, stats, JSON logs, join alerts. Paper 26.2, Java 25.
```

## Version checklist

| Field | Value |
|-------|-------|
| Game version | 26.2 |
| Loader | Paper |
| Version | 4.0 |
| JAR | lock-end-4.0.jar |

## New in 4.0 (for description)

- `/endlock unlockin 7d` and `/endlock unlockat 2026-07-01 18:00`
- PlaceholderAPI: `%lockend_status%`, `%lockend_remaining%`, `%lockend_blocked_count%`
- Join notification when End is locked
- Statistics + JSON logging + `/endlock export`
- LuckPerms context `lockend_locked`
- Optional DiscordSRV notifications
- Clickable update link for admins
