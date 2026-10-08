//! Santa Vibes PC bridge (docs/ROADMAP.md 1.2): captures what the PC plays (WASAPI
//! loopback), analyses it with signals-core and sends `SIGN` packets to the TV over UDP.
//! Two executables share this library: `pc-bridge` (console) and `pc-bridge-bg` (no
//! window, logs to a file; what `--autostart on` registers to start with Windows).

pub mod autostart;
pub mod bridge;
pub mod capture;
pub mod log;
pub mod net;
pub mod ws;
