/**
 * Shared, platform-independent EndLock core package.
 *
 * <p>Rules for this package (feature/multiplatform-core.md, section 4):</p>
 * <ul>
 *   <li>No Bukkit, Fabric, NeoForge, Forge or other Minecraft
 *       implementation dependency may appear here; the only dependency is
 *       the shared {@code lock-end-api} module.</li>
 *   <li>Unit tests must run without starting a Minecraft server.</li>
 *   <li>Platform operations are modeled as interfaces here (e.g.
 *       {@code LockStateRepository}, {@code PlayerEvacuationHandler});
 *       each platform provides its own implementation.</li>
 * </ul>
 */
package org.vwtfafa.lockend.core;
