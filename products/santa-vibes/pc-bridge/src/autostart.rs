//! `pc-bridge --autostart on|off`: start the windowless bridge with Windows (DABS,
//! 2026-10-07). `on` copies `pc-bridge-bg.exe` (from next to this executable) to
//! `%LOCALAPPDATA%\SantaVibes\`, registers it under
//! `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` and starts it; `off` stops it and
//! removes the registration. Uses `reg` and `taskkill`, so no extra dependency.

use crate::log::app_dir;
use std::process::Command;

const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
const VALUE: &str = "SantaVibesBridge";
const BG_EXE: &str = "pc-bridge-bg.exe";

fn run(cmd: &mut Command) -> Result<(), String> {
    let out = cmd.output().map_err(|e| format!("{cmd:?}: {e}"))?;
    if out.status.success() {
        Ok(())
    } else {
        Err(format!("{cmd:?}: {}", String::from_utf8_lossy(&out.stderr).trim()))
    }
}

fn stop_running() {
    let _ = Command::new("taskkill").args(["/IM", BG_EXE, "/F"]).output();
}

pub fn set(on: bool) -> Result<String, String> {
    let target = app_dir().join(BG_EXE);
    if !on {
        stop_running();
        let _ = run(Command::new("reg").args(["delete", RUN_KEY, "/v", VALUE, "/f"]));
        return Ok(format!("autostart off; {} left in place (delete it by hand if you want)", target.display()));
    }
    let here = std::env::current_exe().map_err(|e| e.to_string())?;
    let source = here.with_file_name(BG_EXE);
    if !source.exists() {
        return Err(format!("{} not found: build it with `cargo build --release`", source.display()));
    }
    stop_running();
    std::fs::create_dir_all(app_dir()).map_err(|e| e.to_string())?;
    std::fs::copy(&source, &target).map_err(|e| format!("copy to {}: {e}", target.display()))?;
    let value = format!("\"{}\"", target.display());
    run(Command::new("reg").args(["add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", &value, "/f"]))?;
    Command::new(&target).spawn().map_err(|e| format!("start {}: {e}", target.display()))?;
    Ok(format!(
        "autostart on: {} runs at login (started now); log in {}",
        target.display(),
        app_dir().join("pc-bridge.log").display()
    ))
}
