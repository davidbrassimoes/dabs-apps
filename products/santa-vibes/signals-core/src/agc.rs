//! Level normalisation and smoothing. All per-hop, allocation-free.

/// Automatic gain control in the dB domain: maps a level to 0..1 over a window of
/// `range_db` below an adaptive peak. The peak follows rises quickly and falls by
/// `release_db_s`, but never below `min_peak_db`, so silence and hiss stay near 0.
#[derive(Clone, Debug)]
pub struct Agc {
    peak_db: f32,
    range_db: f32,
    release_db_s: f32,
    min_peak_db: f32,
}

impl Agc {
    pub fn new(range_db: f32, release_db_s: f32, min_peak_db: f32) -> Self {
        Self { peak_db: min_peak_db, range_db, release_db_s, min_peak_db }
    }

    pub fn update(&mut self, db: f32, dt: f32) -> f32 {
        self.update_with_floor(db, dt, self.min_peak_db)
    }

    /// Like [`Agc::update`], with the peak also held at or above `floor_db` (a band tied
    /// to the overall level, so leakage and hiss in an empty band read 0).
    pub fn update_with_floor(&mut self, db: f32, dt: f32, floor_db: f32) -> f32 {
        let floor = floor_db.max(self.min_peak_db);
        if db > self.peak_db {
            // ~50 ms attack: one loud transient does not set the scale on its own.
            self.peak_db += (db - self.peak_db) * (dt / 0.05).min(1.0);
        } else {
            self.peak_db = (self.peak_db - self.release_db_s * dt).max(floor);
        }
        ((db - (self.peak_db.max(floor) - self.range_db)) / self.range_db).clamp(0.0, 1.0)
    }

    pub fn peak(&self) -> f32 {
        self.peak_db
    }

    #[cfg(test)]
    pub fn peak_db(&self) -> f32 {
        self.peak_db
    }
}

/// One-pole smoother with separate rise and fall time constants (seconds).
#[derive(Clone, Copy, Debug)]
pub struct Smooth {
    pub value: f32,
    rise_s: f32,
    fall_s: f32,
}

impl Smooth {
    pub fn new(rise_s: f32, fall_s: f32) -> Self {
        Self { value: 0.0, rise_s, fall_s }
    }

    pub fn update(&mut self, x: f32, dt: f32) -> f32 {
        let tau = if x > self.value { self.rise_s } else { self.fall_s };
        self.value += (x - self.value) * (1.0 - (-dt / tau).exp());
        self.value
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn agc_maps_steady_levels_near_the_top_regardless_of_loudness() {
        for level in [-6.0, -20.0, -35.0] {
            let mut a = Agc::new(36.0, 1.0, -50.0);
            let mut v = 0.0;
            for _ in 0..1000 {
                v = a.update(level, 0.01);
            }
            assert!(v > 0.95, "level {level}: {v}");
        }
    }

    #[test]
    fn agc_keeps_hiss_low() {
        let mut a = Agc::new(36.0, 1.0, -50.0);
        for _ in 0..3000 {
            a.update(-90.0, 0.01);
        }
        assert_eq!(a.update(-90.0, 0.01), 0.0);
        assert_eq!(a.peak_db(), -50.0);
    }

    #[test]
    fn smooth_rises_faster_than_it_falls() {
        let mut s = Smooth::new(0.01, 0.2);
        s.update(1.0, 0.02);
        assert!(s.value > 0.8);
        s.update(0.0, 0.02);
        assert!(s.value > 0.7);
    }
}
