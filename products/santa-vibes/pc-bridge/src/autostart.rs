//! `pc-bridge --autostart on|off`: start the windowless bridge with Windows (DABS,
//! 2026-10-07). `on` copies `pc-bridge-bg.exe` (from next to this executable) to
//! `%LOCALAPPDATA%\SantaVibes\`, registers it under
//! `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` and starts it; `off` stops it and
//! removes the registration. Uses `reg` and `taskkill`, so no extra dependency.

use crate::log::app_dir;
use std::io;
use std::process::{Child, Command, Stdio};

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

/// Starts `cmd` so it outlives, and shares nothing with, the caller: stdio goes to the null
/// device (a script reading our stdout would otherwise wait for EOF until the child exits)
/// and, on Windows, it has no console and its own process group. Nulling stdio is not
/// enough there: `CreateProcess` inherits every inheritable handle, including the pipe the
/// caller gave us as stdout. So our own standard handles are made non-inheritable for the
/// spawn and restored after.
pub fn spawn_detached(cmd: &mut Command) -> io::Result<Child> {
    cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const DETACHED_PROCESS: u32 = 0x0000_0008;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
        cmd.creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
        let _guard = win::NoInherit::std_handles();
        return cmd.spawn();
    }
    #[cfg(not(windows))]
    cmd.spawn()
}

#[cfg(windows)]
mod win {
    use std::ffi::c_void;

    const HANDLE_FLAG_INHERIT: u32 = 1;
    const STD_HANDLES: [u32; 3] = [-10i32 as u32, -11i32 as u32, -12i32 as u32];

    extern "system" {
        fn GetStdHandle(which: u32) -> *mut c_void;
        fn GetHandleInformation(h: *mut c_void, flags: *mut u32) -> i32;
        fn SetHandleInformation(h: *mut c_void, mask: u32, flags: u32) -> i32;
    }

    /// Clears the inherit flag on stdin/stdout/stderr until dropped.
    pub struct NoInherit(Vec<*mut c_void>);

    impl NoInherit {
        pub fn std_handles() -> Self {
            let mut changed = Vec::new();
            for which in STD_HANDLES {
                // SAFETY: plain Win32 calls on handles the process owns; failures are ignored.
                unsafe {
                    let h = GetStdHandle(which);
                    let mut flags = 0u32;
                    if h.is_null() || h as isize == -1 || GetHandleInformation(h, &mut flags) == 0 {
                        continue;
                    }
                    if flags & HANDLE_FLAG_INHERIT != 0 && SetHandleInformation(h, HANDLE_FLAG_INHERIT, 0) != 0 {
                        changed.push(h);
                    }
                }
            }
            NoInherit(changed)
        }
    }

    impl Drop for NoInherit {
        fn drop(&mut self) {
            for &h in &self.0 {
                // SAFETY: restores the flag on a handle we cleared above.
                unsafe { SetHandleInformation(h, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT) };
            }
        }
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
    spawn_detached(&mut Command::new(&target)).map_err(|e| format!("start {}: {e}", target.display()))?;
    Ok(format!(
        "autostart on: {} runs at login (started now); log in {}",
        target.display(),
        app_dir().join("pc-bridge.log").display()
    ))
}
