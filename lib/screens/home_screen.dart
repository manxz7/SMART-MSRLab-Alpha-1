import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hijri/hijri_calendar.dart';

import '../main.dart';
import '../services/location_service.dart';
import '../services/prayer_service.dart';
import 'calendar_screen.dart';
import 'drone_screen.dart';
import 'logo_scan_screen.dart';
import 'prayer_screen.dart';
import 'qibla_screen.dart';
import 'scanner_screen.dart';
import 'settings_screen.dart';
import 'tasbeeh_screen.dart';

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — central hub of the SMART SandboxPlayground super app
//
// Displays:
//   • Islamic greeting based on time-of-day
//   • Current Gregorian + Hijri date
//   • Upcoming prayer time banner (auto-loaded from GPS)
//   • Feature grid: Prayer, Qibla, Halal Search, Scanner, Tasbeeh, Calendar
//   • Settings shortcut in header
// ─────────────────────────────────────────────────────────────────────────────
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // ── Time ──────────────────────────────────────────────────────────────────
  DateTime _now = DateTime.now();
  Timer? _clockTimer;

  // ── Prayer times (loaded in background) ──────────────────────────────────
  PrayerTimesData? _prayerTimes;
  String _nextPrayerName = '';
  String _nextPrayerTime = '';
  Duration _timeUntilNext = Duration.zero;
  bool _prayerLoading = true;

  @override
  void initState() {
    super.initState();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() {
        _now = DateTime.now();
        if (_prayerTimes != null) _updateNextPrayer();
      });
    });
    _loadPrayerTimes();
  }

  @override
  void dispose() {
    _clockTimer?.cancel();
    super.dispose();
  }

  // ── Load prayer times silently from GPS ───────────────────────────────────
  Future<void> _loadPrayerTimes() async {
    try {
      final location = await LocationService.getLocation();
      final times = await PrayerService.getPrayerTimes(
        lat: location.latitude,
        lng: location.longitude,
        country: location.country,
      );
      if (mounted) {
        setState(() {
          _prayerTimes = times;
          _prayerLoading = false;
          _updateNextPrayer();
        });
      }
    } catch (_) {
      // Silently fail — home screen still works without prayer times
      if (mounted) setState(() => _prayerLoading = false);
    }
  }

  // ── Figure out which prayer is next ───────────────────────────────────────
  void _updateNextPrayer() {
    if (_prayerTimes == null) return;
    final prayers = [
      ('Fajr', _prayerTimes!.fajr),
      ('Sunrise', _prayerTimes!.sunrise),
      ('Dhuhr', _prayerTimes!.dhuhr),
      ('Asr', _prayerTimes!.asr),
      ('Maghrib', _prayerTimes!.maghrib),
      ('Isha', _prayerTimes!.isha),
    ];
    for (final p in prayers) {
      if (p.$2.isAfter(_now)) {
        _nextPrayerName = p.$1;
        _nextPrayerTime = _fmt12(p.$2);
        _timeUntilNext = p.$2.difference(_now);
        return;
      }
    }
    // Past Isha — next is tomorrow's Fajr
    _nextPrayerName = 'Fajr (tomorrow)';
    _nextPrayerTime = _fmt12(_prayerTimes!.fajr.add(const Duration(days: 1)));
    _timeUntilNext = _prayerTimes!.fajr.add(const Duration(days: 1)).difference(_now);
  }

  // ── Format time as HH:MM am/pm ────────────────────────────────────────────
  String _fmt12(DateTime dt) {
    final h = dt.hour % 12 == 0 ? 12 : dt.hour % 12;
    final m = dt.minute.toString().padLeft(2, '0');
    final suffix = dt.hour < 12 ? 'am' : 'pm';
    return '$h:$m $suffix';
  }

  // ── Islamic greeting based on time-of-day ────────────────────────────────
  String get _greeting {
    final h = _now.hour;
    if (h < 12) return 'Selamat Pagi';
    if (h < 15) return 'Selamat Tengahari';
    if (h < 18) return 'Selamat Petang';
    return 'Selamat Malam';
  }

  // ── Countdown format: "Xh Ym" or "Xm Ys" ────────────────────────────────
  String _fmtCountdown(Duration d) {
    if (d.inHours > 0) {
      return '${d.inHours}h ${d.inMinutes.remainder(60)}m';
    }
    if (d.inMinutes > 0) {
      return '${d.inMinutes}m ${d.inSeconds.remainder(60)}s';
    }
    return '${d.inSeconds}s';
  }

  @override
  Widget build(BuildContext context) {
    final hijri = HijriCalendar.now();
    final gregorianStr = _fmtGregorian(_now);
    final hijriStr = '${hijri.hDay} ${_hijriMonth(hijri.hMonth)} ${hijri.hYear} AH';

    return Scaffold(
      backgroundColor: const Color(0xFFF0F4F0),
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            // ── Header ──────────────────────────────────────────────────────
            SliverToBoxAdapter(child: _buildHeader(gregorianStr, hijriStr)),

            // ── Prayer countdown banner ──────────────────────────────────────
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
                child: _buildPrayerBanner(),
              ),
            ),

            // ── Section label ────────────────────────────────────────────────
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(20, 16, 20, 8),
                child: Text(
                  'Features',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF2D4A35),
                  ),
                ),
              ),
            ),

            // ── Feature grid ─────────────────────────────────────────────────
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
              sliver: SliverGrid(
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  mainAxisSpacing: 14,
                  crossAxisSpacing: 14,
                  childAspectRatio: 1.05,
                ),
                delegate: SliverChildListDelegate(_featureTiles(context)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Header card ───────────────────────────────────────────────────────────
  Widget _buildHeader(String gregorianStr, String hijriStr) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          colors: [Color(0xFF0D5C30), Color(0xFF1B7A4B)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(28)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Top row: app name + settings
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'SMART',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 3,
                    ),
                  ),
                  Text(
                    'SandboxPlayground',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.75),
                      fontSize: 12,
                      letterSpacing: 1,
                    ),
                  ),
                ],
              ),
              IconButton(
                icon: const Icon(Icons.settings_outlined, color: Colors.white, size: 26),
                tooltip: 'Settings',
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SettingsScreen()),
                ),
              ),
            ],
          ),

          const SizedBox(height: 20),

          // Greeting
          Text(
            '$_greeting!',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          const Text(
            'Assalamu Alaikum Warahmatullahi Wabarakatuh',
            style: TextStyle(color: Colors.white70, fontSize: 11),
          ),

          const SizedBox(height: 16),

          // Date row
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                const Icon(Icons.calendar_today, color: Colors.white70, size: 16),
                const SizedBox(width: 8),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      gregorianStr,
                      style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                    Text(
                      hijriStr,
                      style: const TextStyle(color: Colors.white70, fontSize: 11),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ── Prayer countdown banner ───────────────────────────────────────────────
  Widget _buildPrayerBanner() {
    return Container(
      margin: const EdgeInsets.only(top: 16),
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF1B7A4B).withValues(alpha: 0.10),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF1B7A4B).withValues(alpha: 0.10),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.mosque_outlined, color: Color(0xFF1B7A4B), size: 22),
          ),
          const SizedBox(width: 14),
          if (_prayerLoading)
            const Expanded(
              child: Text('Loading prayer times…', style: TextStyle(color: Colors.grey, fontSize: 13)),
            )
          else if (_nextPrayerName.isEmpty)
            const Expanded(
              child: Text('Tap Prayer Times for details', style: TextStyle(color: Colors.grey, fontSize: 13)),
            )
          else
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Next: $_nextPrayerName  •  $_nextPrayerTime',
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      color: Color(0xFF1B4332),
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'in ${_fmtCountdown(_timeUntilNext)}',
                    style: const TextStyle(color: Color(0xFF1B7A4B), fontSize: 12),
                  ),
                ],
              ),
            ),
          TextButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const PrayerScreen()),
            ),
            child: const Text('View all', style: TextStyle(fontSize: 12, color: Color(0xFF1B7A4B))),
          ),
        ],
      ),
    );
  }

  // ── Feature tiles ─────────────────────────────────────────────────────────
  List<Widget> _featureTiles(BuildContext context) {
    final features = [
      _FeatureInfo(
        label: 'Prayer Times',
        icon: Icons.access_time_rounded,
        color: const Color(0xFF1B7A4B),
        subtitle: 'Waktu solat harian',
        screen: const PrayerScreen(),
      ),
      _FeatureInfo(
        label: 'Qibla Compass',
        icon: Icons.explore_rounded,
        color: const Color(0xFF2E7D9A),
        subtitle: 'Arah kiblat',
        screen: const QiblaScreen(),
      ),
      _FeatureInfo(
        label: 'Halal Checker',
        icon: Icons.verified_rounded,
        color: const Color(0xFF2D6A4F),
        subtitle: 'Cari produk halal JAKIM',
        screen: const SearchScreen(),
      ),
      _FeatureInfo(
        label: 'Scan Product',
        icon: Icons.document_scanner_rounded,
        color: const Color(0xFF6B4C9A),
        subtitle: 'Scan label produk',
        screen: const ScannerScreen(),
      ),
      _FeatureInfo(
        label: 'Tasbeeh',
        icon: Icons.radio_button_checked_rounded,
        color: const Color(0xFFA05C2A),
        subtitle: 'Zikir & tasbih digital',
        screen: const TasbeehScreen(),
      ),
      _FeatureInfo(
        label: 'Hijri Calendar',
        icon: Icons.calendar_month_rounded,
        color: const Color(0xFF7B6D2F),
        subtitle: 'Takwim Islam',
        screen: const CalendarScreen(),
      ),
      _FeatureInfo(
        label: 'Drone Scanner',
        icon: Icons.flight_rounded,
        color: const Color(0xFF1565C0),
        subtitle: 'Scan halal label via DJI Mini 2',
        screen: const DroneScreen(),
      ),
      _FeatureInfo(
        label: 'Scan Logo Halal',
        icon: Icons.verified_rounded,
        color: const Color(0xFF2E7D4F),
        subtitle: 'Kenal pasti badan pensijilan halal',
        screen: const LogoScanScreen(),
      ),
    ];

    return features
        .map((f) => _FeatureTile(info: f))
        .toList();
  }

  // ── Date formatter ────────────────────────────────────────────────────────
  String _fmtGregorian(DateTime dt) {
    const months = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    return '${days[dt.weekday - 1]}, ${dt.day} ${months[dt.month - 1]} ${dt.year}';
  }

  String _hijriMonth(int m) {
    const names = [
      '', 'Muharram', 'Safar', "Rabi' al-Awwal", "Rabi' al-Thani",
      'Jumada al-Awwal', 'Jumada al-Thani', 'Rajab', "Sha'ban",
      'Ramadan', 'Shawwal', "Dhu al-Qi'dah", 'Dhu al-Hijjah',
    ];
    return names[m];
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data class for feature tiles
// ─────────────────────────────────────────────────────────────────────────────
class _FeatureInfo {
  final String label;
  final IconData icon;
  final Color color;
  final String subtitle;
  final Widget screen;

  const _FeatureInfo({
    required this.label,
    required this.icon,
    required this.color,
    required this.subtitle,
    required this.screen,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual feature tile widget
// ─────────────────────────────────────────────────────────────────────────────
class _FeatureTile extends StatelessWidget {
  final _FeatureInfo info;
  const _FeatureTile({required this.info});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      shadowColor: info.color.withValues(alpha: 0.2),
      elevation: 3,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => info.screen),
        ),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Icon badge
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: info.color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(info.icon, color: info.color, size: 26),
              ),
              const Spacer(),
              Text(
                info.label,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                  color: Color(0xFF1B2A1F),
                ),
              ),
              const SizedBox(height: 3),
              Text(
                info.subtitle,
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey.shade500,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
