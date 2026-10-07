//! `pc-bridge`: the console bridge. See lib.rs and README.md.
//!
//!   pc-bridge [--tv <ip>[:port]]... [--device <name part>] [--lookahead <ms>] [--quiet]
//!   pc-bridge --list
//!   pc-bridge --autostart on|off

use pc_bridge::{autostart, bridge, capture};

fn main() {
    let args = bridge::parse_args(std::env::args().skip(1)).unwrap_or_else(|e| {
        eprintln!("{e}");
        std::process::exit(2)
    });
    if args.list {
        for (name, def) in capture::list_outputs() {
            println!("{} {name}", if def { "*" } else { " " });
        }
        return;
    }
    if let Some(on) = args.autostart {
        match autostart::set(on) {
            Ok(m) => println!("{m}"),
            Err(e) => {
                eprintln!("{e}");
                std::process::exit(1)
            }
        }
        return;
    }
    bridge::run(args);
}
