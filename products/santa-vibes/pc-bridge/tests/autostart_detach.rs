//! `--autostart on` hung scripts that read its stdout: the background process inherited the
//! pipe, so EOF only came when it exited. The test re-runs itself as a "script" with a piped
//! stdout; that script starts a 6 s child through `spawn_detached` and exits at once. The
//! reader must see EOF long before the child ends.

use pc_bridge::autostart::spawn_detached;
use std::io::Read;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

const ENV: &str = "PC_BRIDGE_DETACH_HELPER";

fn long_child() -> Command {
    if cfg!(windows) {
        let mut c = Command::new("ping");
        c.args(["-n", "7", "127.0.0.1"]);
        c
    } else {
        let mut c = Command::new("sleep");
        c.arg("6");
        c
    }
}

#[test]
fn detached_child_does_not_hold_the_callers_stdout() {
    if std::env::var(ENV).is_ok() {
        // Helper mode: behave like the script's callee, print, start the child, exit.
        println!("started");
        spawn_detached(&mut long_child()).expect("spawn");
        return;
    }
    let started = Instant::now();
    let mut helper = Command::new(std::env::current_exe().unwrap())
        .args(["--exact", "detached_child_does_not_hold_the_callers_stdout", "--nocapture"])
        .env(ENV, "1")
        .stdout(Stdio::piped())
        .spawn()
        .expect("helper");
    let mut out = String::new();
    helper.stdout.take().unwrap().read_to_string(&mut out).expect("read");
    helper.wait().expect("wait");
    assert!(out.contains("started"), "helper output: {out}");
    assert!(
        started.elapsed() < Duration::from_secs(4),
        "stdout stayed open {:?}: the child inherited it",
        started.elapsed()
    );
}
