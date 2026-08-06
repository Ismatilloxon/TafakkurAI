package quran

import "testing"

// The ayah table is the guard that keeps a malformed or malicious reference out of the
// schedule, so it is worth checking against the real boundaries of the mushaf rather than
// only against obviously-wrong input.
func TestValidRef(t *testing.T) {
	cases := []struct {
		name  string
		surah int
		ayah  int
		want  bool
	}{
		{"first ayah of the mushaf", 1, 1, true},
		{"last ayah of Al-Fatihah", 1, 7, true},
		{"one past Al-Fatihah", 1, 8, false},
		{"last ayah of Al-Baqarah", 2, 286, true},
		{"one past Al-Baqarah", 2, 287, false},
		{"last surah, last ayah", 114, 6, true},
		{"one past the last surah", 115, 1, false},
		{"surah zero", 0, 1, false},
		{"negative surah", -1, 1, false},
		{"ayah zero", 2, 0, false},
		{"negative ayah", 2, -5, false},
		{"At-Tawbah is a normal surah here", 9, 129, true},
		{"shortest surah", 108, 3, true},
		{"one past the shortest surah", 108, 4, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := ValidRef(tc.surah, tc.ayah); got != tc.want {
				t.Errorf("ValidRef(%d, %d) = %v, want %v", tc.surah, tc.ayah, got, tc.want)
			}
		})
	}
}

// 6,236 is the canonical Hafs total. If the table were mistyped anywhere, this catches it —
// and every progress percentage in the app is counted against this same number.
func TestAyahCountsTotal(t *testing.T) {
	total := 0
	for _, n := range ayahCounts {
		total += n
	}
	if total != 6236 {
		t.Errorf("total ayahs = %d, want 6236", total)
	}
}
