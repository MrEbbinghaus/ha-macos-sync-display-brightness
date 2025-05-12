# macOS display brightness to HA

Listens to [lunar](https://lunar.fyi) for changes in display brightness and publishes the value to a [Home Assistant](https://www.home-assistant.io/) `input_number` entity using [HAs REST API](https://developers.home-assistant.io/docs/api/rest/).

## Todo
- [ ] Implement bidirectional sync
- [ ] Don't depend on lunar.

## Configuration

Two environment variables:
- `HASS_SERVER` - URL of the HA instance 
- `HASS_TOKEN` - API token for HA (see https://developers.home-assistant.io/docs/api/rest/)

One constant in the code:
- `ha-monitor-brightness-entity` is the entity id of the target `input_number`. E.g.: `input_number.primary_monitor_brightness`

## Installation as a [LaunchAgent](https://support.apple.com/guide/terminal/script-management-with-launchd-apdc6c1077b-5d5d-4d35-9c19-60f2397b2369/mac)

```console
$ # Copy (or symlink) the plist file to ~/Library/LaunchAgents
$ ln -s <path-to-the-repo>/me.ebbinghaus.display-brightness-to-ha.plist ~/Library/LaunchAgents/me.ebbinghaus.display-brightness-to-ha.plist
$ # Load it with `launchctl load`
$ launchctl load ~/Library/LaunchAgents/me.ebbinghaus.display-brightness-to-ha.plist
```