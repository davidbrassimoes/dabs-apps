//! The hot path (push_interleaved, take, encode_sign) must not allocate after new().
//! Own test binary: the counting allocator is global to it.

mod common;

use signals_core::packet::{encode_sign, flags_of, SourceKind};
use signals_core::{Analyzer, Config};
use std::alloc::{GlobalAlloc, Layout, System};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

struct Counting;
static ARMED: AtomicBool = AtomicBool::new(false);
static COUNT: AtomicUsize = AtomicUsize::new(0);

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, l: Layout) -> *mut u8 {
        if ARMED.load(Ordering::Relaxed) {
            COUNT.fetch_add(1, Ordering::Relaxed);
        }
        System.alloc(l)
    }
    unsafe fn dealloc(&self, p: *mut u8, l: Layout) {
        System.dealloc(p, l)
    }
}

#[global_allocator]
static A: Counting = Counting;

#[test]
fn push_take_and_encode_allocate_nothing() {
    let (audio, _) = common::kicks(48000, 12.0, 0.5, |_| 124.0);
    let stereo: Vec<f32> = audio.iter().flat_map(|&v| [v, v]).collect();
    let mut a = Analyzer::new(48000, Config::default());
    let mut seq = 0u32;
    ARMED.store(true, Ordering::SeqCst);
    for block in stereo.chunks(2 * 480) {
        a.push_interleaved(block, 2);
        let s = a.take();
        let p = encode_sign(&s, SourceKind::PcLoopback, flags_of(&s), seq, seq * 10);
        seq = seq.wrapping_add(p[8] as u32 | 1);
    }
    ARMED.store(false, Ordering::SeqCst);
    assert!(a.peek().tempo_valid, "the run must reach the tempo and phase code paths");
    assert_eq!(COUNT.load(Ordering::SeqCst), 0, "allocations on the hot path");
}
