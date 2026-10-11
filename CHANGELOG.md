# Changelog

## 2026-10-06

- Made the live-session template list expandable and collapsible; it starts compact to leave more room for live controls.
- Added receiver-matched 16:9 slide previews to song creation, including alignment, typography, padding, and background styling controls.
- Improved multi-device media URLs by routing playback to the active WebSocket receiver address, and added suffix byte-range support for video seeking.
- Enabled immersive receiver output and requests a 4K display mode on compatible TVs; replaced raw ACK UI text with concise acknowledgement toasts.
- Trade-offs: 4K selection depends on the TV exposing a compatible display mode. Device-specific playback and long-duration memory profiling still require physical hardware.

## 2026-10-05

- Receiver starts each WebSocket session blank/black and ACKs connection, clear, and black state with layer visibility.
- Added protocol v2 handshake metadata, optional layer/style/template/media commands, and serialization tests for shared models.
- Added a reusable hue wheel/RGB/HEX/alpha color picker and DataStore color persistence on controller and receiver.
- Added controller-to-receiver chunked media uploads over HTTP, receiver cache storage and byte-range streaming, plus fill/fit/stretch and Media3 playback controls.
- Added five built-in templates and a 16:9 editor for persistent custom templates; custom styles can be duplicated, renamed, deleted, made default, and applied live.
- Trade-offs: receiver media cache is reclaimable and files are capped at 2 GiB. Device soak testing and real TV codec validation remain necessary; video thumbnails use a generic tile until playback begins.
# Changelog
