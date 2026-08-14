# Paper Plugin Development

This project targets Paper 26.2.

## API Documentation

Use the official Paper API documentation and Javadocs as the primary reference:

* https://docs.papermc.io/paper/dev/api/
* https://papermc.io/javadocs/
* https://jd.papermc.io/paper/26.2/

## Rules

* Prefer Paper API over Bukkit API when Paper provides an equivalent API.
* Do not invent Paper API classes, methods or events.
* Before using an unfamiliar Paper API, check the official Javadocs.
* Follow the API version configured in build.gradle.
* Do not use deprecated APIs when a modern Paper API exists.
* Keep compatibility with the project's configured Minecraft/Paper version.

