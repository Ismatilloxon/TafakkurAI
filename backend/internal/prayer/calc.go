// Package prayer computes the five daily prayer times from latitude, longitude,
// date, calculation method (Fajr/Isha angles) and madhab (Asr shadow factor).
// The algorithm follows the standard sun-position method used by PrayTimes.org.
package prayer

import (
	"math"
	"time"
)

type Method struct {
	Name        string
	FajrAngle   float64
	IshaAngle   float64 // used when IshaMinutes == 0
	IshaMinutes int     // fixed interval after Maghrib (e.g. Umm al-Qura)
	MaghribMins int
}

// Methods keyed by the identifiers used across the app / DB.
var Methods = map[string]Method{
	"MWL":     {Name: "MWL", FajrAngle: 18, IshaAngle: 17},
	"ISNA":    {Name: "ISNA", FajrAngle: 15, IshaAngle: 15},
	"Egypt":   {Name: "Egypt", FajrAngle: 19.5, IshaAngle: 17.5},
	"Makkah":  {Name: "Makkah", FajrAngle: 18.5, IshaMinutes: 90},
	"Karachi": {Name: "Karachi", FajrAngle: 18, IshaAngle: 18},
	"Tehran":  {Name: "Tehran", FajrAngle: 17.7, IshaAngle: 14},
	"Jafari":  {Name: "Jafari", FajrAngle: 16, IshaAngle: 14},
}

func MethodByName(name string) (Method, bool) {
	m, ok := Methods[name]
	return m, ok
}

// AsrShadowFactor is 2 for the Hanafi madhab, 1 for everyone else.
func AsrShadowFactor(madhab string) float64 {
	if madhab == "hanafi" {
		return 2
	}
	return 1
}

type Times struct {
	Fajr    time.Time
	Sunrise time.Time
	Dhuhr   time.Time
	Asr     time.Time
	Maghrib time.Time
	Isha    time.Time
}

func dtr(d float64) float64 { return d * math.Pi / 180 }
func rtd(r float64) float64 { return r * 180 / math.Pi }

func fixAngle(a float64) float64 {
	a = math.Mod(a, 360)
	if a < 0 {
		a += 360
	}
	return a
}

func fixHour(h float64) float64 {
	h = math.Mod(h, 24)
	if h < 0 {
		h += 24
	}
	return h
}

func julian(year, month, day int) float64 {
	if month <= 2 {
		year--
		month += 12
	}
	a := math.Floor(float64(year) / 100)
	b := 2 - a + math.Floor(a/4)
	return math.Floor(365.25*float64(year+4716)) +
		math.Floor(30.6001*float64(month+1)) + float64(day) + b - 1524.5
}

// sunPosition returns declination (deg) and equation of time (hours).
func sunPosition(jd float64) (decl, eqt float64) {
	d := jd - 2451545.0
	g := fixAngle(357.529 + 0.98560028*d)
	q := fixAngle(280.459 + 0.98564736*d)
	l := fixAngle(q + 1.915*math.Sin(dtr(g)) + 0.020*math.Sin(dtr(2*g)))
	e := 23.439 - 0.00000036*d
	ra := rtd(math.Atan2(math.Cos(dtr(e))*math.Sin(dtr(l)), math.Cos(dtr(l)))) / 15
	ra = fixHour(ra)
	eqt = q/15 - ra
	decl = rtd(math.Asin(math.Sin(dtr(e)) * math.Sin(dtr(l))))
	return
}

// hourAngle returns hours from midday for the sun to reach the given depression
// angle below the horizon (positive angle = below horizon).
func hourAngle(angle, lat, decl float64) float64 {
	x := (-math.Sin(dtr(angle)) - math.Sin(dtr(lat))*math.Sin(dtr(decl))) /
		(math.Cos(dtr(lat)) * math.Cos(dtr(decl)))
	return rtd(math.Acos(x)) / 15
}

func asrHourAngle(factor, lat, decl float64) float64 {
	alt := rtd(math.Atan2(1, factor+math.Tan(dtr(math.Abs(lat-decl)))))
	x := (math.Sin(dtr(alt)) - math.Sin(dtr(lat))*math.Sin(dtr(decl))) /
		(math.Cos(dtr(lat)) * math.Cos(dtr(decl)))
	return rtd(math.Acos(x)) / 15
}

// Calculate returns the day's prayer times in loc.
func Calculate(date time.Time, lat, lng float64, loc *time.Location, m Method, asrFactor float64) Times {
	y, mo, d := date.Date()
	_, tzSec := date.In(loc).Zone()
	tz := float64(tzSec) / 3600

	jDate := julian(y, int(mo), d) - lng/(15*24)
	decl, eqt := sunPosition(jDate + 0.5)
	midDay := fixHour(12 - eqt)
	offset := tz - lng/15

	sunrise := midDay - hourAngle(0.833, lat, decl)
	sunset := midDay + hourAngle(0.833, lat, decl)
	maghrib := sunset + float64(m.MaghribMins)/60

	isha := midDay + hourAngle(m.IshaAngle, lat, decl)
	if m.IshaMinutes > 0 {
		isha = maghrib + float64(m.IshaMinutes)/60
	}

	raw := map[string]float64{
		"fajr":    midDay - hourAngle(m.FajrAngle, lat, decl),
		"sunrise": sunrise,
		"dhuhr":   midDay,
		"asr":     midDay + asrHourAngle(asrFactor, lat, decl),
		"maghrib": maghrib,
		"isha":    isha,
	}

	build := func(h float64) time.Time {
		if math.IsNaN(h) {
			return time.Time{}
		}
		base := time.Date(y, mo, d, 0, 0, 0, 0, loc)
		return base.Add(time.Duration(fixHour(h+offset) * float64(time.Hour))).Round(time.Minute)
	}

	return Times{
		Fajr:    build(raw["fajr"]),
		Sunrise: build(raw["sunrise"]),
		Dhuhr:   build(raw["dhuhr"]),
		Asr:     build(raw["asr"]),
		Maghrib: build(raw["maghrib"]),
		Isha:    build(raw["isha"]),
	}
}
