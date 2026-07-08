package prayer

import (
	"testing"
	"time"
)

// Tashkent: 41.2995 N, 69.2401 E, UTC+5. We assert ordering, that Dhuhr lands
// near local solar noon, and that Hanafi Asr is later than Standard Asr. Exact
// minute values differ by source, so we test with a tolerance rather than
// hardcoding a single authority.
func tashkent(t *testing.T) *time.Location {
	loc, err := time.LoadLocation("Asia/Tashkent")
	if err != nil {
		t.Skipf("tzdata unavailable: %v", err)
	}
	return loc
}

func TestCalculate_TashkentOrdering(t *testing.T) {
	loc := tashkent(t)
	date := time.Date(2026, 6, 21, 0, 0, 0, 0, loc)
	m := Methods["MWL"]

	times := Calculate(date, 41.2995, 69.2401, loc, m, AsrShadowFactor("shafii"))

	seq := []struct {
		name string
		t    time.Time
	}{
		{"fajr", times.Fajr},
		{"sunrise", times.Sunrise},
		{"dhuhr", times.Dhuhr},
		{"asr", times.Asr},
		{"maghrib", times.Maghrib},
		{"isha", times.Isha},
	}
	for i := 1; i < len(seq); i++ {
		if !seq[i].t.After(seq[i-1].t) {
			t.Errorf("%s (%s) is not after %s (%s)",
				seq[i].name, seq[i].t.Format("15:04"),
				seq[i-1].name, seq[i-1].t.Format("15:04"))
		}
	}
}

func TestCalculate_DhuhrNearSolarNoon(t *testing.T) {
	loc := tashkent(t)
	date := time.Date(2026, 6, 21, 0, 0, 0, 0, loc)
	times := Calculate(date, 41.2995, 69.2401, loc, Methods["MWL"], 1)

	// Solar noon in Tashkent in late June is ~12:40 local. Allow ±15 min.
	want := time.Date(2026, 6, 21, 12, 40, 0, 0, loc)
	diff := times.Dhuhr.Sub(want)
	if diff < -15*time.Minute || diff > 15*time.Minute {
		t.Errorf("Dhuhr = %s, want within 15m of %s",
			times.Dhuhr.Format("15:04"), want.Format("15:04"))
	}
}

func TestCalculate_HanafiAsrLater(t *testing.T) {
	loc := tashkent(t)
	date := time.Date(2026, 6, 21, 0, 0, 0, 0, loc)

	std := Calculate(date, 41.2995, 69.2401, loc, Methods["MWL"], AsrShadowFactor("shafii"))
	hanafi := Calculate(date, 41.2995, 69.2401, loc, Methods["MWL"], AsrShadowFactor("hanafi"))

	if !hanafi.Asr.After(std.Asr) {
		t.Errorf("Hanafi Asr (%s) should be after Standard Asr (%s)",
			hanafi.Asr.Format("15:04"), std.Asr.Format("15:04"))
	}
}
