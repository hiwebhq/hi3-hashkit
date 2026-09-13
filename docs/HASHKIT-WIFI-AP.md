# Hashkit WiFi field AP

A pocket access point (reference hardware: GL.iNet GL-MT300N-V2 "Mango"; anything
OpenWrt-based works) that a technician plugs into any switch on a mining LAN. The
phone joins the `Hi3-Hashkit` SSID and lands directly on the miner subnet, so Site
Map, the network scanner, live logs and every other Hashkit tool work as if the
phone were cabled in.

## Approved configuration (the spec)

| Item | Setting |
|---|---|
| Operating mode | Access Point / **Layer-2 bridge** (never routed) |
| SSID | `Hi3-Hashkit` |
| Ethernet uplink | site switch |
| NAT | **disabled** |
| DHCP server | **disabled** (see the caveat — never enable it) |
| Client isolation | disabled |
| Wi-Fi security | WPA2/WPA3 |
| Internet access | unnecessary |
| Device management | blocked from the site LAN where practical |
| Physical reset button | restores this approved configuration |
| QR code sticker on device | auto-joins the Hashkit Wi-Fi |
| Auto-shutdown / Wi-Fi timeout | 30–60 minutes |

## THE BIG CAVEAT — DHCP

The phone must receive an address **on the mining subnet** (miners `10.40.12.0/24`
→ phone e.g. `10.40.12.57`). If the mining LAN provides no DHCP, the phone
self-assigns `169.254.x.x` and scanning fails.

**Never "fix" this by enabling a DHCP server on the AP.** A rogue DHCP server on a
production mining switch can renumber or disrupt the whole subnet. The correct
fallback is a **static IP on the phone** inside the site's known subnet (Hashkit
knows each farm's subnets and suggests a safe address); the app detects the
link-local condition and walks the user through it.

## OpenWrt provisioning (GL.iNet, SSH as root)

```sh
# --- Layer-2 bridge AP: wifi joins br-lan, wan port becomes part of the bridge ---
uci set network.lan.proto='dhcp'          # AP mgmt takes a lease if offered; fine if none
uci del network.lan.ipaddr 2>/dev/null
uci del dhcp.lan.ignore 2>/dev/null
uci set dhcp.lan.ignore='1'               # DHCP SERVER OFF (the caveat)
uci set dhcp.wan.ignore='1' 2>/dev/null
/etc/init.d/dnsmasq disable               # belt and braces: no rogue DHCP, ever

# Put the wan ethernet port into the lan bridge (Mango: eth0.2 varies per model —
# check `uci show network` on your firmware).
uci del_list network.@device[0].ports='eth0.2' 2>/dev/null
uci add_list network.@device[0].ports='eth0.2'

# --- Wi-Fi ---
uci set wireless.@wifi-iface[0].ssid='Hi3-Hashkit'
uci set wireless.@wifi-iface[0].encryption='sae-mixed'   # WPA2/WPA3
uci set wireless.@wifi-iface[0].key='<CHOOSE-A-PASSPHRASE>'
uci set wireless.@wifi-iface[0].isolate='0'              # client isolation OFF
uci set wireless.@wifi-iface[0].network='lan'
uci set wireless.radio0.disabled='0'

# --- Management hardening: LuCI/SSH only from wifi clients, not the site LAN ---
# (Where practical; exact firewall stanzas vary by firmware. Minimum: change the
# root password and disable WAN-side management in the GL.iNet UI.)

# --- Auto Wi-Fi timeout: radio off 45 min after boot; power-cycle to restart ---
cat >> /etc/rc.local <<'RC'
( sleep 2700 && wifi down ) &
RC

uci commit && reboot
```

**Reset-button behavior**: after first provisioning, take a config backup
(`sysupgrade -b /root/hi3-approved.tar.gz` and store a copy off-device). GL.iNet's
reset restores factory firmware; re-apply the approved config from the backup
(`sysupgrade -r`). For true one-press restore, bake the config into a custom
image with `owut`/imagebuilder — optional hardening for later.

**QR sticker**: Hashkit's Hashkit-WiFi settings screen generates the standard
`WIFI:T:WPA;S:Hi3-Hashkit;P:<passphrase>;;` join QR for printing (same printer
flow as the miner asset tags).

## What the app does (Settings → Hashkit WiFi)

1. Join QR generator + one-tap Android network suggestion for `Hi3-Hashkit`.
2. Connection status: detects when the phone is on the Hashkit SSID, shows the
   acquired address and subnet.
3. **Lease sanity check**: a `169.254.*` address raises a clear warning with the
   static-IP fallback instructions, pre-filled from the active farm's stored
   subnets — and explicitly reminds why the AP's DHCP must stay off.
4. One-tap "Scan this network" plus shortcuts to Site Map and the log tools.
5. Setup checklist mirroring the approved-configuration table above.
