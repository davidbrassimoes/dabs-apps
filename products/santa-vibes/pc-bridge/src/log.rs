//! Output: the console, or a log file for the windowless `pc-bridge-bg`.

use std::fs::{File, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

static FILE: Mutex<Option<File>> = Mutex::new(None);
/// A log bigger than this is moved to `<name>.old` at start.
const MAX_BYTES: u64 = 1 << 20;

/// `%LOCALAPPDATA%\SantaVibes`, where the background bridge lives and logs.
pub fn app_dir() -> PathBuf {
    let base = std::env::var_os("LOCALAPPDATA").map(PathBuf::from).unwrap_or_else(std::env::temp_dir);
    base.join("SantaVibes")
}

/// Send all further lines to `path` (appending; rotated once it passes 1 MB).
pub fn to_file(path: &Path) -> std::io::Result<()> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    if std::fs::metadata(path).map(|m| m.len() > MAX_BYTES).unwrap_or(false) {
        let _ = std::fs::rename(path, path.with_extension("log.old"));
    }
    let f = OpenOptions::new().create(true).append(true).open(path)?;
    *FILE.lock().unwrap() = Some(f);
    Ok(())
}

pub fn line(msg: &str) {
    let mut f = FILE.lock().unwrap();
    match f.as_mut() {
        Some(file) => {
            let secs = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
            let _ = writeln!(file, "{secs} {msg}");
        }
        None => println!("{msg}"),
    }
}

/// `say!("...", args)`: one line to the console or the log file.
#[macro_export]
macro_rules! say {
    ($($t:tt)*) => { $crate::log::line(&format!($($t)*)) };
}
