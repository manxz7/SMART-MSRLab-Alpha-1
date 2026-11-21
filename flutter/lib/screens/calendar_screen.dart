import 'package:flutter/material.dart';
import 'package:hijri/hijri_calendar.dart';

// ─────────────────────────────────────────────────────────────────────────────
// CalendarScreen — shows a Hijri (Islamic) calendar grid for the current month
//
// The hijri package converts Gregorian dates to Hijri dates.
// We build a full month grid: each cell shows both Gregorian and Hijri day.
// ─────────────────────────────────────────────────────────────────────────────
class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  // Track which month/year the user is viewing
  // Start with current Hijri month
  late int _viewYear;
  late int _viewMonth;

  @override
  void initState() {
    super.initState();
    // HijriCalendar.now() gives today's Hijri date
    final today = HijriCalendar.now();
    _viewYear  = today.hYear;
    _viewMonth = today.hMonth;
  }

  // Navigate to previous Hijri month
  void _prevMonth() {
    setState(() {
      _viewMonth--;
      if (_viewMonth < 1) {
        _viewMonth = 12;
        _viewYear--;
      }
    });
  }

  // Navigate to next Hijri month
  void _nextMonth() {
    setState(() {
      _viewMonth++;
      if (_viewMonth > 12) {
        _viewMonth = 1;
        _viewYear++;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final today = HijriCalendar.now();

    // Get how many days are in this Hijri month
    // Hijri months alternate between 29 and 30 days (lunar calendar)
    // getDaysInMonth is an instance method — create a temporary object to call it
    final daysInMonth = HijriCalendar()
        .getDaysInMonth(_viewYear, _viewMonth);

    // Find what day of week the 1st falls on (0=Mon ... 6=Sun)
    // We convert the Hijri 1st to Gregorian to use DateTime.weekday
    final firstOfMonth = HijriCalendar()
      ..hYear  = _viewYear
      ..hMonth = _viewMonth
      ..hDay   = 1;
    final firstGregorian = firstOfMonth.hijriToGregorian(
        _viewYear, _viewMonth, 1);
    // weekday: 1=Mon, 7=Sun → we want 0-indexed starting from Sunday
    // Sunday = 0, Monday = 1, ... Saturday = 6
    final startOffset = firstGregorian.weekday % 7;

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: SafeArea(
        child: Column(
          children: [
            // ── Header ─────────────────────────────────────────────────────
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
              decoration: const BoxDecoration(
                color: Color(0xFF1a7a4a),
                borderRadius:
                    BorderRadius.vertical(bottom: Radius.circular(24)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Hijri Calendar',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text(
                    'Today: ${today.hDay} ${_monthName(today.hMonth)} ${today.hYear} AH',
                    style: const TextStyle(color: Colors.white70, fontSize: 13),
                  ),
                ],
              ),
            ),

            // ── Month navigation ───────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(
                    onPressed: _prevMonth,
                    icon: const Icon(Icons.chevron_left),
                    color: const Color(0xFF1a7a4a),
                  ),
                  Column(
                    children: [
                      Text(
                        _monthName(_viewMonth),
                        style: const TextStyle(
                            fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        '$_viewYear AH',
                        style: const TextStyle(color: Colors.grey, fontSize: 13),
                      ),
                    ],
                  ),
                  IconButton(
                    onPressed: _nextMonth,
                    icon: const Icon(Icons.chevron_right),
                    color: const Color(0xFF1a7a4a),
                  ),
                ],
              ),
            ),

            // ── Day-of-week header row ─────────────────────────────────────
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
                    .map((d) => Expanded(
                          child: Center(
                            child: Text(
                              d,
                              style: TextStyle(
                                color: d == 'Fri'
                                    ? const Color(0xFF1a7a4a)
                                    : Colors.grey,
                                fontWeight: FontWeight.w600,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        ))
                    .toList(),
              ),
            ),

            const SizedBox(height: 8),

            // ── Calendar grid ──────────────────────────────────────────────
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: GridView.builder(
                  physics: const NeverScrollableScrollPhysics(),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 7,
                    childAspectRatio: 0.85,
                  ),
                  itemCount: startOffset + daysInMonth,
                  itemBuilder: (context, index) {
                    // Empty cells before the 1st of the month
                    if (index < startOffset) return const SizedBox();

                    final day = index - startOffset + 1;
                    final isToday = day == today.hDay &&
                        _viewMonth == today.hMonth &&
                        _viewYear == today.hYear;

                    // Get the Gregorian equivalent for this Hijri day
                    final gregDate = firstOfMonth
                        .hijriToGregorian(_viewYear, _viewMonth, day);
                    final isFriday = gregDate.weekday == 5;

                    return _DayCell(
                      hijriDay: day,
                      gregDay: gregDate.day,
                      isToday: isToday,
                      isFriday: isFriday,
                    );
                  },
                ),
              ),
            ),

            // ── Legend ─────────────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _legendDot(const Color(0xFF1a7a4a), 'Today'),
                  const SizedBox(width: 20),
                  _legendDot(const Color(0xFF1a7a4a).withOpacity(0.15),
                      'Friday'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _legendDot(Color color, String label) {
    return Row(
      children: [
        Container(
            width: 12,
            height: 12,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 6),
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
      ],
    );
  }

  // Hijri month names in order
  String _monthName(int month) {
    const names = [
      'Muharram', 'Safar', 'Rabi\' Al-Awwal', 'Rabi\' Al-Akhir',
      'Jumada Al-Ula', 'Jumada Al-Akhirah', 'Rajab', 'Sha\'ban',
      'Ramadan', 'Shawwal', 'Dhul Qa\'dah', 'Dhul Hijjah',
    ];
    return names[(month - 1).clamp(0, 11)];
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// _DayCell — one cell in the calendar grid
// Shows the Hijri day number (large) and Gregorian day (small below)
// ─────────────────────────────────────────────────────────────────────────────
class _DayCell extends StatelessWidget {
  final int hijriDay;
  final int gregDay;
  final bool isToday;
  final bool isFriday;

  const _DayCell({
    required this.hijriDay,
    required this.gregDay,
    required this.isToday,
    required this.isFriday,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        color: isToday
            ? const Color(0xFF1a7a4a)
            : isFriday
                ? const Color(0xFF1a7a4a).withOpacity(0.12)
                : Colors.white,
        borderRadius: BorderRadius.circular(10),
        boxShadow: isToday
            ? [BoxShadow(
                color: const Color(0xFF1a7a4a).withOpacity(0.3),
                blurRadius: 6,
                offset: const Offset(0, 2),
              )]
            : null,
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '$hijriDay',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: isToday ? Colors.white : Colors.black87,
            ),
          ),
          Text(
            '$gregDay',
            style: TextStyle(
              fontSize: 10,
              color: isToday
                  ? Colors.white70
                  : isFriday
                      ? const Color(0xFF1a7a4a)
                      : Colors.grey,
            ),
          ),
        ],
      ),
    );
  }
}
