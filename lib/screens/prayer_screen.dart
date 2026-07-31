import 'dart:async';
import 'package:flutter/material.dart';
import '../main.dart';
import '../services/location_service.dart';
import '../services/notification_service.dart';
import '../services/prayer_service.dart';
import '../widgets/location_picker_dialog.dart';

// ─────────────────────────────────────────────────────────────────────────────
// PrayerScreen — shows all 6 prayer times with countdown to next prayer
// ─────────────────────────────────────────────────────────────────────────────
class PrayerScreen extends StatefulWidget {
  const PrayerScreen({super.key});

  @override
  State<PrayerScreen> createState() => _PrayerScreenState();
}

class _PrayerScreenState extends State<PrayerScreen> {
  LocationData? _location;
  PrayerTimesData? _prayerTimes;
  bool _loading = true;
  String? _error;
  DateTime _now = DateTime.now();
  Timer? _clockTimer;

  @override
  void initState() {
    super.initState();
    _load();
    // Tick every second to update countdown and current/next prayer highlights
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() => _now = DateTime.now());
    });
  }

  @override
  void dispose() {
    _clockTimer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final location = await LocationService.getLocation();
      await _loadFromLocation(location);
      // Update city name in header once geocoding completes in background
      LocationService.geocodeInBackground(location, (updated) {
        if (mounted) setState(() => _location = updated);
      });
    } catch (e) {
      setState(() { _error = e.toString(); _loading = false; });
    }
  }

  // ── Shared helper: fetch prayer times for a given location ────────────────
  // Used by both GPS (_load) and manual picker (_showLocationPicker).
  Future<void> _loadFromLocation(LocationData location) async {
    final times = await PrayerService.getPrayerTimes(
      lat: location.latitude,
      lng: location.longitude,
      country: location.country,
    );
    setState(() {
      _location = location;
      _prayerTimes = times;
      _loading = false;
      _error = null;
    });

    // Schedule a notification for each prayer time today.
    // Reads 'adhan_enabled' from gPrefs — if the user turned it off, does nothing.
    final adhanOn = gPrefs.getBool('adhan_enabled') ?? true;
    await schedulePrayerNotifications(
      prayerTimes: [
        times.fajr,
        times.sunrise,
        times.dhuhr,
        times.asr,
        times.maghrib,
        times.isha,
      ],
      adhanEnabled: adhanOn,
    );
  }

  // ── Manual location picker ─────────────────────────────────────────────
  Future<void> _showLocationPicker() async {
    final selected = await showLocationPicker(context);
    if (selected != null && mounted) {
      setState(() { _loading = true; _error = null; });
      try {
        await _loadFromLocation(selected);
      } catch (e) {
        setState(() { _error = e.toString(); _loading = false; });
      }
    }
  }

  // ── Determine which prayer is current and which is next ───────────────────
  // Returns (currentIndex, nextIndex) where index is into [fajr,sunrise,dhuhr,asr,maghrib,isha]
  (int, int) _getCurrentAndNext(List<DateTime> times) {
    int current = -1;
    int next = 0;
    for (int i = 0; i < times.length - 1; i++) {
      if (_now.isAfter(times[i]) && _now.isBefore(times[i + 1])) {
        current = i;
        next = i + 1;
        break;
      }
    }
    if (_now.isAfter(times.last)) {
      current = times.length - 1;
      next = 0; // after Isha → next is tomorrow's Fajr
    }
    return (current, next);
  }

  // ── Format duration as HH:MM:SS countdown ────────────────────────────────
  String _formatCountdown(DateTime target) {
    var diff = target.difference(_now);
    if (diff.isNegative) diff = Duration.zero;
    final h = diff.inHours.toString().padLeft(2, '0');
    final m = (diff.inMinutes % 60).toString().padLeft(2, '0');
    final s = (diff.inSeconds % 60).toString().padLeft(2, '0');
    return '$h:$m:$s';
  }

  // ── Format time as "5:49 AM" ──────────────────────────────────────────────
  String _formatTime(DateTime dt) {
    final h = dt.hour > 12 ? dt.hour - 12 : (dt.hour == 0 ? 12 : dt.hour);
    final m = dt.minute.toString().padLeft(2, '0');
    final ampm = dt.hour >= 12 ? 'PM' : 'AM';
    return '$h:$m $ampm';
  }

  // ── Hijri date display ────────────────────────────────────────────────────
  String _hijriDisplay() {
    if (_prayerTimes?.hijri != null) {
      // API gives "1447-10-06" → reformat to "6 Shawwal 1447"
      final parts = _prayerTimes!.hijri!.split('-');
      if (parts.length == 3) {
        const months = [
          'Muharram','Safar','Rabi Al-Awwal','Rabi Al-Akhir',
          'Jumada Al-Ula','Jumada Al-Akhirah','Rajab','Sha\'ban',
          'Ramadan','Shawwal','Dhul Qa\'dah','Dhul Hijjah',
        ];
        final monthIdx = int.tryParse(parts[1]);
        final monthName = (monthIdx != null && monthIdx >= 1 && monthIdx <= 12)
            ? months[monthIdx - 1]
            : parts[1];
        return '${int.parse(parts[2])} $monthName ${parts[0]}';
      }
    }
    // Fallback: compute from Intl-style offset (approximate)
    final now = DateTime.now();
    final hijriYear = now.year - 579;
    return '$hijriYear AH';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: _loading
            ? _buildLoading()
            : _error != null
                ? _buildError()
                : _buildContent(),
      ),
    );
  }

  Widget _buildLoading() => const Center(
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        CircularProgressIndicator(color: Color(0xFF1a7a4a)),
        SizedBox(height: 16),
        Text('Loading prayer times...', style: TextStyle(color: Colors.grey)),
      ],
    ),
  );

  Widget _buildError() => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.red),
          const SizedBox(height: 16),
          Text(_error!, textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.grey)),
          const SizedBox(height: 24),
          ElevatedButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Try GPS Again'),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF1a7a4a),
              foregroundColor: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _showLocationPicker,
            icon: const Icon(Icons.list_alt),
            label: const Text('Choose location manually'),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF1a7a4a),
              side: const BorderSide(color: Color(0xFF1a7a4a)),
            ),
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF1a7a4a).withOpacity(0.07),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Row(
              children: [
                Icon(Icons.tips_and_updates_outlined,
                    color: Color(0xFF1a7a4a), size: 18),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'GPS is slow indoors. Pick your city manually — '
                    'prayer times still use the official JAKIM data!',
                    style: TextStyle(fontSize: 12, color: Color(0xFF1a7a4a)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );

  Widget _buildContent() {
    final pt = _prayerTimes!;
    final times = [pt.fajr, pt.sunrise, pt.dhuhr, pt.asr, pt.maghrib, pt.isha];
    final names = ['Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
    final icons = [
      Icons.brightness_3, Icons.wb_sunny_outlined, Icons.wb_sunny,
      Icons.cloud_queue, Icons.wb_twilight, Icons.nights_stay,
    ];

    final (currentIdx, nextIdx) = _getCurrentAndNext(times);
    final nextTime = nextIdx == 0
        ? pt.fajr.add(const Duration(days: 1)) // tomorrow's Fajr
        : times[nextIdx];

    return Column(
      children: [
        // ── Header ──────────────────────────────────────────────────────────
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
          decoration: const BoxDecoration(
            color: Color(0xFF1a7a4a),
            borderRadius: BorderRadius.vertical(bottom: Radius.circular(24)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Prayer Times',
                          style: TextStyle(color: Colors.white, fontSize: 22,
                              fontWeight: FontWeight.bold)),
                      const SizedBox(height: 2),
                      Row(children: [
                        const Icon(Icons.location_on, color: Colors.white70, size: 14),
                        const SizedBox(width: 4),
                        Text(
                          _location != null
                              ? '${_location!.city}, ${_location!.region}'
                              : '',
                          style: const TextStyle(color: Colors.white70, fontSize: 13),
                        ),
                      ]),
                    ],
                  ),
                  // Source badge + change location button
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          pt.source == 'WaktuSolat'
                              ? '🏛 ${pt.zone ?? 'MY'}'
                              : '🌍 Calculated',
                          style: const TextStyle(color: Colors.white, fontSize: 11),
                        ),
                      ),
                      const SizedBox(width: 8),
                      // ── Change location button ─────────────────────────
                      Tooltip(
                        message: 'Change location',
                        child: IconButton(
                          onPressed: _showLocationPicker,
                          icon: const Icon(Icons.edit_location_alt_outlined,
                              color: Colors.white),
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          style: IconButton.styleFrom(
                            backgroundColor: Colors.white.withOpacity(0.15),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Date row
              Text(
                _formatDate(),
                style: const TextStyle(color: Colors.white70, fontSize: 13),
              ),
              Text(
                _hijriDisplay(),
                style: const TextStyle(color: Colors.white, fontSize: 15,
                    fontWeight: FontWeight.w500),
              ),
            ],
          ),
        ),

        // ── Next prayer countdown card ────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.07),
                  blurRadius: 10,
                  offset: const Offset(0, 3),
                )
              ],
            ),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1a7a4a).withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.access_time,
                      color: Color(0xFF1a7a4a), size: 24),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Next Prayer',
                        style: TextStyle(color: Colors.grey, fontSize: 12)),
                    Text(
                      nextIdx == 0 ? 'Fajr (tomorrow)' : names[nextIdx],
                      style: const TextStyle(fontWeight: FontWeight.bold,
                          fontSize: 16),
                    ),
                  ],
                ),
                const Spacer(),
                Text(
                  _formatCountdown(nextTime),
                  style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF1a7a4a),
                  ),
                ),
              ],
            ),
          ),
        ),

        // ── Prayer list ───────────────────────────────────────────────────
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: times.length,
            itemBuilder: (context, i) {
              final isCurrent = i == currentIdx;
              final isNext    = i == nextIdx;
              return _prayerCard(
                name: names[i],
                time: _formatTime(times[i]),
                icon: icons[i],
                isCurrent: isCurrent,
                isNext: isNext,
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _prayerCard({
    required String name,
    required String time,
    required IconData icon,
    required bool isCurrent,
    required bool isNext,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: isCurrent
            ? const Color(0xFF1a7a4a)
            : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: isNext
            ? Border.all(color: const Color(0xFF1a7a4a), width: 1.5)
            : null,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Icon(icon,
              color: isCurrent ? Colors.white : const Color(0xFF1a7a4a),
              size: 22),
          const SizedBox(width: 14),
          Text(
            name,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: isCurrent ? Colors.white : Colors.black87,
            ),
          ),
          const Spacer(),
          // NOW / NEXT badge
          if (isCurrent)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              margin: const EdgeInsets.only(right: 10),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.25),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Text('NOW',
                  style: TextStyle(color: Colors.white, fontSize: 10,
                      fontWeight: FontWeight.bold)),
            ),
          if (isNext && !isCurrent)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              margin: const EdgeInsets.only(right: 10),
              decoration: BoxDecoration(
                color: const Color(0xFF1a7a4a).withOpacity(0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Text('NEXT',
                  style: TextStyle(color: Color(0xFF1a7a4a), fontSize: 10,
                      fontWeight: FontWeight.bold)),
            ),
          Text(
            time,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: isCurrent ? Colors.white : Colors.black87,
            ),
          ),
        ],
      ),
    );
  }

  String _formatDate() {
    const months = [
      'January','February','March','April','May','June',
      'July','August','September','October','November','December'
    ];
    const days = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
    final d = _now;
    return '${days[d.weekday - 1]}, ${d.day} ${months[d.month - 1]} ${d.year}';
  }
}
