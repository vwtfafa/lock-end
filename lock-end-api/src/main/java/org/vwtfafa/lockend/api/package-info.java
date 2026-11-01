/**
 * Shared, platform-independent EndLock API package.
 *
 * <p>Rules for this package (feature/multiplatform-core.md, section 3):</p>
 * <ul>
 *   <li>No Bukkit, Paper, Fabric, NeoForge, Forge or other Minecraft
 *       implementation dependency may appear here; the module must stay
 *       plain Java.</li>
 *   <li>Data structures are immutable and safe to share across threads.</li>
 *   <li>Enum members are not a closed set in the compatibility contract:
 *       future versions may add values, so consumers must tolerate unknown
 *       values.</li>
 * </ul>
 */
package org.vwtfafa.lockend.api;
