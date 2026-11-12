import 'dart:math';
import 'package:flutter/material.dart';
import '../services/location_service.dart';
import '../widgets/location_picker_dialog.dart';

// ─────────────────────────────────────────────────────────────────────────────
// QiblaScreen — shows a compass arrow pointing toward Mecca
//
// This screen has 3 states:
//   Loading: waiting for GPS + first sensor reading
//   Error:   GPS permission denied or sensor unavailable
//   Normal:  compass is working, arrow animates smoothly
// ─────────────────────────────────────────────────────────────────────────────
class QiblaScreen extends StatefulWidget {
  const QiblaScreen({super.key});

  @override
  State<QiblaScreen> createState() => _QiblaScreenState();
}

class _QiblaScreenState extends State<QiblaScreen>
    with SingleTickerProviderStateMixin {
  // ── State variables ──────────────────────────────────────────────────────
  LocationData? _location;      // user's GPS + city info
  double _compassHeading = 0;   // which way the phone is pointing (degrees)
  double _qiblaAngle = 0;       // bearing from user to Mecca (degrees)
  double _compassAccuracy = 999; // sensor accuracy (degrees uncertainty; lower = better)
  double? _magneticDeclination;   // degrees East (+) or West (-), null until loaded
  bool _loading = true;
  String? _error;

  // ── Animation ─────────────────────────────────────────────────────────────
  // AnimationController drives the smooth rotation animation
  // vsync: this — ties animation to screen refresh rate (efficient)
  late AnimationController _animController;
  late Animation<double> _rotationAnimation;
  double _prevRotation = 0; // last rotation value, so we can animate from it

  // ── Compass stream subscription ───────────────────────────────────────────
  // StreamSubscription lets us stop listening to the sensor when screen closes
  dynamic _compassSubscription;

  @override
  void initState() {
    super.initState();

    // Set up the animation controller — duration = how long each rotation takes
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );

    _rotationAnimation = Tween<double>(begin: 0, end: 0).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeOut),
    );

    _loadLocation(); // start loading GPS when screen first appears
  }

  @override
  void dispose() {
    // Always cancel subscriptions and controllers when the widget is destroyed
    // Otherwise they keep running in the background and cause memory leaks
    _compassSubscription?.cancel();
    _animController.dispose();
    super.dispose();
  }

  // ── Load GPS and start compass ────────────────────────────────────────────
  Future<void> _loadLocation() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final location = await LocationService.getLocation();
      _applyLocation(location);
      // Geocoding runs in background — compass is already showing at this point.
      // When geocoding finishes, we update the city name in the header.
      LocationService.geocodeInBackground(location, (updated) {
        if (mounted) setState(() => _location = updated);
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  // ── Apply a LocationData (from GPS or manual picker) and start compass ────
  // Extracted into its own method so both _loadLocation and _showLocationPicker
  // can use the same logic without repeating code.
  void _applyLocation(LocationData location) {
    setState(() {
      _location = location;
      _qiblaAngle = QiblaService.calculateQiblaAngle(
        location.latitude,
        location.longitude,
      );
      _loading = false;
      _error = null;
    });

    // Fetch magnetic declination for this location in background
    QiblaService.getMagneticDeclination(location.latitude, location.longitude)
        .then((dec) { if (mounted) setState(() => _magneticDeclination = dec); });

    // Cancel any previous compass subscription before starting a new one
    _compassSubscription?.cancel();

    // Reset accumulated rotation state so new location starts clean
    _prevRotation = 0;
    _rotationAnimation = Tween<double>(begin: 0, end: 0).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeOut),
    );

    // Start listening to the magnetometer
    _compassSubscription = QiblaService.startCompass(
      location.latitude,
      location.longitude,
    ).listen(
      (values) {
      final heading = values[0];
      final needleRotation = values[2];
      final accuracy = values[3];

      // Smooth rotation: find the shortest angular path to avoid spinning
      // e.g. going from 350° to 10° should rotate +20°, not -340°
      // Normalise _prevRotation to [0,360) before delta so accumulated
      // value never breaks the single-correction short-path calculation.
      final prevNorm = _prevRotation % 360;
      double delta = needleRotation - prevNorm;
      if (delta > 180) delta -= 360;
      if (delta < -180) delta += 360;
      final targetRotation = _prevRotation + delta;

      // Animate from current position to new position
      _rotationAnimation = Tween<double>(
        begin: _rotationAnimation.value,
        end: targetRotation,
      ).animate(CurvedAnimation(
        parent: _animController,
        curve: Curves.easeOut,
      ));
      _animController.forward(from: 0);

      setState(() {
        _compassHeading = heading;
        _compassAccuracy = accuracy;
        _prevRotation = targetRotation;
      });
    },
    // If the compass stream throws (e.g. sensor unavailable on this device),
    // show the error on screen instead of silently doing nothing
    onError: (Object e) {
      if (mounted) {
        setState(() {
          _error = 'Compass error: $e\n\nTry choosing location manually, or restart the app.';
          _loading = false;
        });
      }
    },
    onDone: () {
      // Stream closed unexpectedly — sensor became unavailable
      if (mounted && _compassAccuracy >= 999) {
        setState(() {
          _error = 'Compass sensor stopped. Tap "Try GPS Again" to restart.';
          _loading = false;
        });
      }
    },
    cancelOnError: false,
    );
  }

  // ── Manual location picker ───────────────────────────────────────────────
  Future<void> _showLocationPicker() async {
    final selected = await showLocationPicker(context);
    if (selected != null && mounted) {
      _applyLocation(selected);
      LocationService.geocodeInBackground(selected, (updated) {
        if (mounted) setState(() => _location = updated);
      });
    }
  }

  // ── Build UI ──────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(
              child: _loading
                  ? _buildLoading()
                  : _error != null
                      ? _buildError()
                      : _buildCompass(),
            ),
          ],
        ),
      ),
    );
  }

  // ── Header ────────────────────────────────────────────────────────────────
  Widget _buildHeader() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 20, 12, 16),
      decoration: const BoxDecoration(
        color: Color(0xFF1a7a4a),
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(24)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Qibla Compass',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Icon(Icons.location_on, color: Colors.white70, size: 14),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Text(
                        _location != null
                            ? '${_location!.city}, ${_location!.region}'
                            : 'Detecting location...',
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          // ── Change location button ────────────────────────────────────────
          // Always visible so the user can check Qibla for any city at any time
          Tooltip(
            message: 'Change location',
            child: IconButton(
              onPressed: _showLocationPicker,
              icon: const Icon(Icons.edit_location_alt_outlined, color: Colors.white),
              style: IconButton.styleFrom(
                backgroundColor: Colors.white.withOpacity(0.15),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── Loading state ─────────────────────────────────────────────────────────
  Widget _buildLoading() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(color: Color(0xFF1a7a4a)),
          SizedBox(height: 16),
          Text('Getting your location...', style: TextStyle(color: Colors.grey)),
        ],
      ),
    );
  }

  // ── Error state ───────────────────────────────────────────────────────────
  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.location_off, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text(
              _error!,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.grey),
            ),
            const SizedBox(height: 24),
            // ── Primary action: retry GPS ──────────────────────────────────
            ElevatedButton.icon(
              onPressed: _loadLocation,
              icon: const Icon(Icons.refresh),
              label: const Text('Try GPS Again'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF1a7a4a),
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ── Secondary action: skip GPS, pick city manually ─────────────
            // This always works even without GPS or internet!
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
            // ── Tip card ──────────────────────────────────────────────────
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
                      'GPS is slow indoors. Use "Choose location manually" '
                      'to pick your nearest city — the compass still works perfectly!',
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
  }

  // ── Calibration banner ────────────────────────────────────────────────────
  // Shown when compass accuracy is MEDIUM, LOW, or UNRELIABLE (999).
  Widget? _buildCalibrationBanner() {
    if (_compassAccuracy <= 45) return null;  // good accuracy — hide banner
    final bool unreliable = _compassAccuracy >= 999;
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 400),
      child: Container(
        key: const ValueKey('calib'),
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: Colors.red.shade50,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Colors.red.shade300),
        ),
        child: Row(
          children: [
            _Figure8Icon(),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    unreliable
                        ? 'Compass unreliable — calibration required'
                        : 'Compass needs calibration',
                    style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: Colors.red)),
                  const SizedBox(height: 2),
                  const Text(
                    'Wave your phone in a figure-8 pattern to improve accuracy.',
                    style: TextStyle(fontSize: 11, color: Colors.red),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Main compass UI ───────────────────────────────────────────────────────
  // Uses LayoutBuilder to detect screen width and switch layouts:
  //   Phone  (width ≤ 600): single column — compass on top, cards below
  //   Tablet (width  > 600): two columns  — compass left, cards right
  Widget _buildCompass() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isTablet = constraints.maxWidth > 600;
        // Scale compass to fit the screen nicely
        final compassSize = isTablet
            ? (constraints.maxWidth * 0.40).clamp(280.0, 420.0)
            : (constraints.maxWidth * 0.75).clamp(220.0, 300.0);

        final compassWidget = _buildCompassDial2(compassSize);
        final infoWidget    = _buildInfoPanel(isTablet);

        if (isTablet) {
          // ── Tablet: side-by-side layout ────────────────────────────────
          return SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                if (_buildCalibrationBanner() != null) _buildCalibrationBanner()!,
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Left column — compass
                    Expanded(
                      flex: 5,
                      child: Column(
                        children: [
                          const SizedBox(height: 16),
                          compassWidget,
                        ],
                      ),
                    ),
                    const SizedBox(width: 32),
                    // Right column — all info cards
                    Expanded(
                      flex: 5,
                      child: infoWidget,
                    ),
                  ],
                ),
              ],
            ),
          );
        }

        // ── Phone: stacked layout ──────────────────────────────────────
        return SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              if (_buildCalibrationBanner() != null) _buildCalibrationBanner()!,
              const SizedBox(height: 16),
              compassWidget,
              const SizedBox(height: 16),
              infoWidget,
            ],
          ),
        );
      },
    );
  }

  // ── Compass dial + arrow, sized dynamically ───────────────────────────────
  Widget _buildCompassDial2(double size) {
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(size: Size(size, size), painter: _CompassDialPainter()),
          AnimatedBuilder(
            animation: _rotationAnimation,
            builder: (context, child) => Transform.rotate(
              angle: _rotationAnimation.value * pi / 180,
              child: child,
            ),
            child: CustomPaint(size: Size(size, size), painter: _ArrowPainter()),
          ),
          AnimatedBuilder(
            animation: _rotationAnimation,
            builder: (context, child) {
              final rad = _rotationAnimation.value * pi / 180;
              return Transform.translate(
                offset: Offset((size * 0.357) * sin(rad), -(size * 0.357) * cos(rad)),
                child: child,
              );
            },
            child: Text('🕋', style: TextStyle(fontSize: size * 0.079)),
          ),
        ],
      ),
    );
  }

  // ── Info panel (accuracy badge + cards + tip) ─────────────────────────────
  // Extracted so both phone and tablet layouts can reuse the same panel.
  Widget _buildInfoPanel(bool isTablet) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (isTablet) const SizedBox(height: 16),

        // ── Accuracy badge ──────────────────────────────────────────────
        Center(
          child: () {
            if (_compassAccuracy >= 999) {
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.grey.shade300),
                ),
                child: Text('Initializing sensor...',
                    style: TextStyle(fontSize: 13, color: Colors.grey.shade700)),
              );
            }
            final String label;
            final Color bg, fg;
            if (_compassAccuracy <= 10) {
              label = '✅  HIGH accuracy  ±${_compassAccuracy.toStringAsFixed(0)}°';
              bg = Colors.green.shade50; fg = Colors.green.shade800;
            } else if (_compassAccuracy <= 45) {
              label = '⚠️  MEDIUM  ±${_compassAccuracy.toStringAsFixed(0)}° — wave in figure-8';
              bg = Colors.orange.shade50; fg = Colors.orange.shade800;
            } else {
              label = '❌  LOW  ±${_compassAccuracy.toStringAsFixed(0)}° — needs calibration';
              bg = Colors.red.shade50; fg = Colors.red.shade800;
            }
            return Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: bg,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: fg.withValues(alpha: 0.35)),
              ),
              child: Text(label,
                  style: TextStyle(fontSize: 13, color: fg, fontWeight: FontWeight.w600)),
            );
          }(),
        ),
        const SizedBox(height: 16),

        // ── Qibla + Heading cards ───────────────────────────────────────
        Row(
          children: [
            Expanded(child: _infoCard(label: 'Qibla Direction',
                value: '${_qiblaAngle.toStringAsFixed(1)}°', icon: Icons.explore)),
            const SizedBox(width: 12),
            Expanded(child: _infoCard(label: 'Compass Heading',
                value: '${_compassHeading.toStringAsFixed(1)}°', icon: Icons.navigation)),
          ],
        ),
        const SizedBox(height: 12),

        // ── Magnetic declination card ───────────────────────────────────
        _buildDeclinationCard(),
        const SizedBox(height: 12),

        // ── Tip card ────────────────────────────────────────────────────
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFF1a7a4a).withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Row(
            children: [
              Icon(Icons.info_outline, color: Color(0xFF1a7a4a), size: 18),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Wait for HIGH accuracy badge before comparing with other apps. '
                  'Hold device flat, away from USB cables and metal objects.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF1a7a4a)),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ── Magnetic declination card ─────────────────────────────────────────────
  Widget _buildDeclinationCard() {
    final dec = _magneticDeclination;
    final String value;
    final String subtitle;
    if (dec == null) {
      value = '—';
      subtitle = 'Loading...';
    } else {
      final sign = dec >= 0 ? '+' : '';
      value = '$sign${dec.toStringAsFixed(2)}°';
      subtitle = dec >= 0 ? 'East declination' : 'West declination';
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          const Icon(Icons.compass_calibration_outlined,
              color: Color(0xFF1a7a4a), size: 24),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Magnetic Declination',
                  style: TextStyle(fontSize: 11, color: Colors.grey)),
              Text(value,
                  style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1a7a4a))),
              Text(subtitle,
                  style: const TextStyle(fontSize: 11, color: Colors.grey)),
            ],
          ),
          const Spacer(),
          Tooltip(
            message: 'The angle between true north and magnetic north at your location.\n'
                'Positive = magnetic north is east of true north.\n'
                'Android\'s sensor fusion already corrects for this.',
            triggerMode: TooltipTriggerMode.tap,
            child: const Icon(Icons.info_outline, size: 18, color: Colors.grey),
          ),
        ],
      ),
    );
  }

  // ── Small info card ───────────────────────────────────────────────────────
  Widget _infoCard({
    required String label,
    required String value,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          Icon(icon, color: const Color(0xFF1a7a4a), size: 22),
          const SizedBox(height: 6),
          Text(value, style: const TextStyle(
            fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF1a7a4a))),
          Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ],
      ),
    );
  }
}

// ── Figure-8 animation widget ─────────────────────────────────────────────
// Animates a dot tracing a figure-8 (Lemniscate of Bernoulli) path.
// Parametric equations: x = cos(t) / (1 + sin²(t)), y = sin(t)cos(t) / (1 + sin²(t))
class _Figure8Icon extends StatefulWidget {
  const _Figure8Icon();
  @override
  State<_Figure8Icon> createState() => _Figure8IconState();
}

class _Figure8IconState extends State<_Figure8Icon>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 36,
      height: 36,
      child: AnimatedBuilder(
        animation: _ctrl,
        builder: (_, __) {
          final t = _ctrl.value * 2 * pi;
          final denom = 1 + sin(t) * sin(t);
          final x = (cos(t) / denom) * 14 + 18;
          final y = (sin(t) * cos(t) / denom) * 14 + 18;
          return CustomPaint(
            painter: _Figure8Painter(dotX: x, dotY: y),
          );
        },
      ),
    );
  }
}

class _Figure8Painter extends CustomPainter {
  final double dotX, dotY;
  const _Figure8Painter({required this.dotX, required this.dotY});

  @override
  void paint(Canvas canvas, Size size) {
    // Draw the figure-8 path
    final pathPaint = Paint()
      ..color = Colors.red.shade200
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    final path = Path();
    for (int i = 0; i <= 120; i++) {
      final t = i / 120 * 2 * pi;
      final d = 1 + sin(t) * sin(t);
      final x = (cos(t) / d) * 14 + size.width / 2;
      final y = (sin(t) * cos(t) / d) * 14 + size.height / 2;
      i == 0 ? path.moveTo(x, y) : path.lineTo(x, y);
    }
    canvas.drawPath(path, pathPaint);
    // Draw the moving dot
    canvas.drawCircle(Offset(dotX, dotY), 3, Paint()..color = Colors.red);
  }

  @override
  bool shouldRepaint(_Figure8Painter old) =>
      old.dotX != dotX || old.dotY != dotY;
}


// CustomPainter — draws the compass ring with N/S/E/W labels and tick marks
//
// CustomPainter lets you draw arbitrary shapes using a Canvas (like a drawing board).
// You use Paint objects (like a paintbrush) to set colour, stroke width etc.
// ─────────────────────────────────────────────────────────────────────────────
class _CompassDialPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 10;

    // ── Outer ring ────────────────────────────────────────────────────────
    final ringPaint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.fill;
    canvas.drawCircle(center, radius, ringPaint);

    final borderPaint = Paint()
      ..color = const Color(0xFF1a7a4a).withOpacity(0.3)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawCircle(center, radius, borderPaint);

    // ── Tick marks — 36 marks every 10 degrees ────────────────────────────
    final tickPaint = Paint()
      ..color = Colors.grey.shade400
      ..strokeWidth = 1.5;

    for (int i = 0; i < 36; i++) {
      final angle = i * 10 * pi / 180;
      final isMajor = i % 9 == 0; // N, E, S, W are major ticks
      final innerR = radius - (isMajor ? 18 : 10);
      final outerR = radius - 4;

      canvas.drawLine(
        Offset(center.dx + innerR * sin(angle), center.dy - innerR * cos(angle)),
        Offset(center.dx + outerR * sin(angle), center.dy - outerR * cos(angle)),
        tickPaint,
      );
    }

    // ── N/S/E/W labels ────────────────────────────────────────────────────
    final labels = {'N': 0.0, 'E': 90.0, 'S': 180.0, 'W': 270.0};
    final labelRadius = radius - 30;

    labels.forEach((label, degrees) {
      final angle = degrees * pi / 180;
      final x = center.dx + labelRadius * sin(angle);
      final y = center.dy - labelRadius * cos(angle);

      final tp = TextPainter(
        text: TextSpan(
          text: label,
          style: TextStyle(
            color: label == 'N' ? const Color(0xFF1a7a4a) : Colors.grey.shade600,
            fontSize: label == 'N' ? 16 : 13,
            fontWeight: label == 'N' ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        textDirection: TextDirection.ltr,
      );
      tp.layout();
      tp.paint(canvas, Offset(x - tp.width / 2, y - tp.height / 2));
    });

    // ── Centre dot ────────────────────────────────────────────────────────
    canvas.drawCircle(
      center,
      6,
      Paint()..color = const Color(0xFF1a7a4a),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// ─────────────────────────────────────────────────────────────────────────────
// Arrow painter — draws a single green upward-pointing arrow
// This will be rotated by Transform.rotate to point toward Mecca
// ─────────────────────────────────────────────────────────────────────────────
class _ArrowPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    const arrowLength = 85.0;
    const arrowWidth = 10.0;

    final paint = Paint()
      ..color = const Color(0xFF1a7a4a)
      ..style = PaintingStyle.fill;

    // Arrow body (rectangle pointing up)
    final bodyRect = Rect.fromCenter(
      center: Offset(center.dx, center.dy - arrowLength / 2 + 10),
      width: arrowWidth,
      height: arrowLength - 20,
    );
    canvas.drawRect(bodyRect, paint);

    // Arrow head (triangle at the top)
    final headPath = Path()
      ..moveTo(center.dx, center.dy - arrowLength)       // tip
      ..lineTo(center.dx - 16, center.dy - arrowLength + 28) // left
      ..lineTo(center.dx + 16, center.dy - arrowLength + 28) // right
      ..close();
    canvas.drawPath(headPath, paint);

    // Arrow tail (small triangle at bottom, slightly lighter)
    final tailPaint = Paint()
      ..color = const Color(0xFF1a7a4a).withOpacity(0.4)
      ..style = PaintingStyle.fill;
    final tailPath = Path()
      ..moveTo(center.dx, center.dy + 20)        // bottom tip
      ..lineTo(center.dx - 8, center.dy + 5)     // left
      ..lineTo(center.dx + 8, center.dy + 5)     // right
      ..close();
    canvas.drawPath(tailPath, tailPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
