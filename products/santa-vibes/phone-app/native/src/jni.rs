//! The JNI entry points for `dev.santavibes.phone.SignalsNative`.
//!
//! No JNI crate: a `JNIEnv*` points to a pointer to the JNI function table, whose slots
//! are fixed by the JNI specification ("JNI Functions", function table indices). Only
//! three are needed: GetArrayLength (171), GetPrimitiveArrayCritical (222) and
//! ReleasePrimitiveArrayCritical (223). Arrays are accessed "critically" (no copy on ART);
//! no other JNI call happens between Get and Release, as the spec requires.

use crate::Engine;
use std::ffi::c_void;

type Env = *mut *const *const c_void;
type JObject = *mut c_void;

const GET_ARRAY_LENGTH: usize = 171;
const GET_PRIMITIVE_ARRAY_CRITICAL: usize = 222;
const RELEASE_PRIMITIVE_ARRAY_CRITICAL: usize = 223;
/// Release mode: free the buffer without copying back (input arrays).
const JNI_ABORT: i32 = 2;

unsafe fn slot(env: Env, i: usize) -> *const c_void {
    *(*env).add(i)
}

unsafe fn array_len(env: Env, a: JObject) -> usize {
    let f: unsafe extern "system" fn(Env, JObject) -> i32 = std::mem::transmute(slot(env, GET_ARRAY_LENGTH));
    f(env, a).max(0) as usize
}

unsafe fn critical(env: Env, a: JObject) -> *mut c_void {
    let f: unsafe extern "system" fn(Env, JObject, *mut u8) -> *mut c_void =
        std::mem::transmute(slot(env, GET_PRIMITIVE_ARRAY_CRITICAL));
    f(env, a, std::ptr::null_mut())
}

unsafe fn release(env: Env, a: JObject, p: *mut c_void, mode: i32) {
    let f: unsafe extern "system" fn(Env, JObject, *mut c_void, i32) =
        std::mem::transmute(slot(env, RELEASE_PRIMITIVE_ARRAY_CRITICAL));
    f(env, a, p, mode)
}

/// `create(sampleRate: Int, lookaheadMs: Float): Long`
#[no_mangle]
pub extern "system" fn Java_dev_santavibes_phone_SignalsNative_create(
    _env: Env,
    _class: JObject,
    sample_rate: i32,
    lookahead_ms: f32,
) -> i64 {
    Box::into_raw(Box::new(Engine::new(sample_rate.max(8000) as u32, lookahead_ms))) as i64
}

/// `destroy(handle: Long)`
#[no_mangle]
pub extern "system" fn Java_dev_santavibes_phone_SignalsNative_destroy(_env: Env, _class: JObject, handle: i64) {
    if handle != 0 {
        unsafe { drop(Box::from_raw(handle as *mut Engine)) };
    }
}

/// `push(handle: Long, samples: FloatArray, count: Int, channels: Int)`: interleaved PCM.
#[no_mangle]
pub extern "system" fn Java_dev_santavibes_phone_SignalsNative_push(
    env: Env,
    _class: JObject,
    handle: i64,
    samples: JObject,
    count: i32,
    channels: i32,
) {
    if handle == 0 || samples.is_null() {
        return;
    }
    let engine = unsafe { &*(handle as *const Engine) };
    unsafe {
        let n = (count.max(0) as usize).min(array_len(env, samples));
        let p = critical(env, samples);
        if p.is_null() {
            return;
        }
        engine.push(std::slice::from_raw_parts(p as *const f32, n), channels.max(1) as usize);
        release(env, samples, p, JNI_ABORT);
    }
}

/// `packet(handle: Long, kind: Int, sequence: Int, timeMs: Int, out: ByteArray): Int` →
/// takes the latest signals, writes a 56-byte `SIGN` packet into `out`, returns its flags.
#[no_mangle]
pub extern "system" fn Java_dev_santavibes_phone_SignalsNative_packet(
    env: Env,
    _class: JObject,
    handle: i64,
    kind: i32,
    sequence: i32,
    time_ms: i32,
    out: JObject,
) -> i32 {
    if handle == 0 || out.is_null() {
        return 0;
    }
    let engine = unsafe { &*(handle as *const Engine) };
    unsafe {
        let n = array_len(env, out);
        let p = critical(env, out);
        if p.is_null() {
            return 0;
        }
        let flags = engine.packet(kind as u8, sequence as u32, time_ms as u32, std::slice::from_raw_parts_mut(p as *mut u8, n));
        release(env, out, p, 0); // 0: copy back (if ART copied) and free
        flags as i32
    }
}
