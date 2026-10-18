# ItemWorthDisplayer 1.2.0

Paper plugin intended to show **only one tooltip line**: `Total value: $...`.

The total is calculated from the item's own EconomyShopGUI sell price plus the sell prices of supported contained items (for example, items inside a shulker box). Example: a shulker box worth $500 containing a diamond worth $25 displays `Total value: $525` only. No separate item price, contents price, or per-item breakdown is shown.

The plugin modifies outgoing item packets so the added tooltip is transient and is not intended to permanently edit stored item metadata. It processes item stacks sent in player inventories and open inventory/container screens. Support for particular container types depends on the Paper API and item metadata available.

## Requirements

- Paper 26.3
- Java 25
- EconomyShopGUI or EconomyShopGUI Premium with a compatible API
- PacketEvents 2.14.0 or newer (for Minecraft 26.3 packet support)
- Maven 3.9+ to build from source

PacketEvents must be installed separately; it is not bundled into this project. The plugin declares `packetevents` as a hard dependency so Paper makes its classes available while loading this plugin.

## Build

From this directory:

```bash
mvn clean package
```

Expected output: `target/ItemWorthDisplayer-1.2.0.jar`, if dependency resolution and compilation succeed.

## Install

1. Install PacketEvents compatible with Minecraft 26.3.
2. Install EconomyShopGUI or EconomyShopGUI Premium and ensure its API is available.
3. Copy the built JAR into the Paper server's `plugins` folder.
4. Restart the server.
5. Adjust `plugins/ItemWorthDisplayer/config.yml` if needed and restart.

## Important notes

This source has not been compiled or integration-tested in this environment. Build and test it on a test server before production use. EconomyShopGUI API methods can differ by release, and permissions, dynamic prices, or multi-economy configurations can affect returned values. Containers without a detectable sell price contribute no value. The configured total is an estimate based on the API results available to the plugin.
