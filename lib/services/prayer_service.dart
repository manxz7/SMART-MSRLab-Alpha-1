import 'dart:convert';
import 'dart:math';
import 'package:http/http.dart' as http;

// ─────────────────────────────────────────────────────────────────────────────
// PrayerTimesData — holds today's 6 prayer times + metadata
// ─────────────────────────────────────────────────────────────────────────────
class PrayerTimesData {
  final DateTime fajr;
  final DateTime sunrise;
  final DateTime dhuhr;
  final DateTime asr;
  final DateTime maghrib;
  final DateTime isha;
  final String source;  // 'WaktuSolat' or 'Calculated'
  final String? zone;   // e.g. 'WLY02' — detected by the API
  final String? hijri;  // e.g. '1447-10-06'

  const PrayerTimesData({
    required this.fajr,
    required this.sunrise,
    required this.dhuhr,
    required this.asr,
    required this.maghrib,
    required this.isha,
    required this.source,
    this.zone,
    this.hijri,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// PrayerService — fetches or calculates prayer times
//
// Strategy:
//   Malaysia (country == 'MY')  →  try api.waktusolat.app GPS endpoint
//   Outside Malaysia / failure  →  calculate offline using solar math
// ─────────────────────────────────────────────────────────────────────────────
class PrayerService {
  static const double _d2r = pi / 180; // degrees → radians

  // ── OPTION A: waktusolat.app API (Malaysia only) ──────────────────────────
  // Endpoint: GET /v2/solat/gps/{lat}/{long}?year=YYYY&month=M
  // API auto-detects zone from GPS. Times = Unix timestamps (seconds).
  static Future<PrayerTimesData?> _fetchFromApi(double lat, double lng) async {
    final now = DateTime.now();
    final url = Uri.parse(
      'https://api.waktusolat.app/v2/solat/gps/$lat/$lng'
      '?year=${now.year}&month=${now.month}',
    );

    try {
      final response = await http.get(url).timeout(const Duration(seconds: 8));
      if (response.statusCode != 200) return null;

      final data = jsonDecode(response.body) as Map<String, dynamic>;
      final prayers = data['prayers'] as List<dynamic>?;
      if (prayers == null) return null;

      // Find today's entry in the month array
      final todayEntry = prayers.cast<Map<String, dynamic>>().firstWhere(
        (p) => p['day'] == now.day,
        orElse: () => {},
      );
      if (todayEntry.isEmpty) return null;

      // Unix timestamp (seconds) → DateTime
      DateTime fromUnix(dynamic ts) =>
          DateTime.fromMillisecondsSinceEpoch((ts as int) * 1000);

      return PrayerTimesData(
        fajr:    fromUnix(todayEntry['fajr']),
        sunrise: fromUnix(todayEntry['syuruk']), // 'syuruk' = Malay for sunrise
        dhuhr:   fromUnix(todayEntry['dhuhr']),
        asr:     fromUnix(todayEntry['asr']),
        maghrib: fromUnix(todayEntry['maghrib']),
        isha:    fromUnix(todayEntry['isha']),
        source: 'WaktuSolat',
        zone: data['zone'] as String?,
        hijri: todayEntry['hijri'] as String?,
      );
    } catch (_) {
      return null; // timeout or network error → use fallback
    }
  }

  // ── OPTION B: Calculate offline (worldwide fallback) ──────────────────────
  // Uses standard solar position formulas (Muslim World League method).
  // Works anywhere with no internet required.
  static PrayerTimesData _calculateOffline(double lat, double lng) {
    final now = DateTime.now();

    // Julian Day Number — astronomers' continuous day count since 4713 BC
    int y = now.year, m = now.month, d = now.day;
    if (m <= 2) { y--; m += 12; }
    final a = y ~/ 100;
    final b = 2 - a + a ~/ 4;
    final jd = (365.25 * (y + 4716)).floor() +
               (30.6001 * (m + 1)).floor() +
               d + b - 1524.5;

    final dd = jd - 2451545.0; // days from J2000.0

    // Sun's mean anomaly and mean longitude (degrees)
    final g = (357.529 + 0.98560028 * dd) % 360;
    final q = (280.459 + 0.98564736 * dd) % 360;
    // Ecliptic longitude
    final l = (q + 1.915 * sin(g * _d2r) + 0.020 * sin(2 * g * _d2r)) % 360;
    // Obliquity of ecliptic
    final e = 23.439 - 0.00000036 * dd;
    // Right ascension and declination
    final ra = atan2(cos(e * _d2r) * sin(l * _d2r), cos(l * _d2r)) / _d2r;
    final decl = asin(sin(e * _d2r) * sin(l * _d2r)); // radians
    // Equation of time (degrees)
    final eqT = q - ra;

    // Solar noon in UTC hours
    final solarNoon = 12 - lng / 15 - eqT / 15;

    // Hour angle for sunrise/sunset (sun centre at -0.833° horizon)
    final latR = lat * _d2r;
    double hourAngle(double angleDeg) {
      final cosHA = (sin(angleDeg * _d2r) - sin(latR) * sin(decl)) /
          (cos(latR) * cos(decl));
      return acos(cosHA.clamp(-1.0, 1.0)) / _d2r / 15; // in hours
    }

    final ha = hourAngle(-0.833);
    final sunrise = solarNoon - ha;
    final sunset  = solarNoon + ha;

    // Asr (Shafi: shadow = object height)
    final noonAlt = (90 - lat.abs() - decl.abs() / _d2r);
    final asrAngle = atan(1 / (1 + tan(noonAlt.abs() * _d2r)));
    final cosAsrHA = (sin(asrAngle) - sin(latR) * sin(decl)) /
        (cos(latR) * cos(decl));
    final asrHa = acos(cosAsrHA.clamp(-1.0, 1.0)) / _d2r / 15;
    final asr = solarNoon + asrHa;

    // Fajr / Isha — Muslim World League depression angles
    final fajr = solarNoon - hourAngle(-18.0);
    final isha  = solarNoon + hourAngle(-17.0);

    // Convert UTC decimal hours → local DateTime
    DateTime toLocal(double utcH) {
      final offsetH = now.timeZoneOffset.inMinutes / 60;
      final local = utcH + offsetH;
      final h = local.floor() % 24;
      final min = ((local - local.floor()) * 60).round();
      return DateTime(now.year, now.month, now.day, h, min);
    }

    return PrayerTimesData(
      fajr:    toLocal(fajr),
      sunrise: toLocal(sunrise),
      dhuhr:   toLocal(solarNoon),
      asr:     toLocal(asr),
      maghrib: toLocal(sunset),
      isha:    toLocal(isha),
      source: 'Calculated',
    );
  }

  // ── Main entry point ──────────────────────────────────────────────────────
  static Future<PrayerTimesData> getPrayerTimes({
    required double lat,
    required double lng,
    required String country,
  }) async {
    // Always try the Malaysian API first — it accepts GPS coordinates and
    // auto-detects the JAKIM zone. Falls back to offline solar math if unavailable.
    final apiResult = await _fetchFromApi(lat, lng);
    if (apiResult != null) return apiResult;
    return _calculateOffline(lat, lng);
  }
}
