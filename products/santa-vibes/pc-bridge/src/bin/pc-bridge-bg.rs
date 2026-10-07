//! `pc-bridge-bg`: the same bridge with no window, logging to
//! `%LOCALAPPDATA%\SantaVibes\pc-bridge.log` (a stats line every minute). This is what
//! `pc-bridge --autostart on` registers to start with Windows. Takes the same options.
#![windows_subsystem = "windows"]

use pc_bridge::{bridge, log, say};
use std::time::Duration;

fn main() {
    let _ = log::to_file(&log::app_dir().join("pc-bridge.log"));
    let mut args = match bridge::parse_args(std::env::args().skip(1)) {
        Ok(a) => a,
        Err(e) => {
            say!("{e}");
            std::process::exit(2)
        }
    };
    args.stats_every = Duration::from_secs(60);
    say!("pc-bridge-bg started");
    bridge::run(args);
}
