import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../main.dart';

// ─────────────────────────────────────────────────────────────────────────────
// TasbeehScreen — digital tasbih/dhikr counter
//
// Features:
//   • Tap to count with haptic feedback
//   • Preset targets: 33 × 3 (Subhanallah, Alhamdulillah, Allahu Akbar) or 99
//   • Circular progress arc
//   • Reset button
//   • Count persists across app restarts via SharedPreferences
// ─────────────────────────────────────────────────────────────────────────────

class TasbeehScreen extends StatefulWidget {
  const TasbeehScreen({super.key});
  @override
  State<TasbeehScreen> createState() => _TasbeehScreenState();
}

class _TasbeehScreenState extends State<TasbeehScreen>
    with SingleTickerProviderStateMixin {
  static const _kCount  = 'tasbeeh_count';
  static const _kTarget = 'tasbeeh_target';

  // Preset dhikr phrases cycled at 33/66/99
  static const _phrases = ['Subhanallah', 'Alhamdulillah', 'Allahu Akbar'];
  static const _targets = [33, 66, 99, 100];

  int _count  = 0;
  int _target = 33;  // default target

  late AnimationController _bounceCtrl;
  late Animation<double> _bounceAnim;

  @override
  void initState() {
    super.initState();
    _count  = gPrefs.getInt(_kCount)  ?? 0;
    _target = gPrefs.getInt(_kTarget) ?? 33;

    _bounceCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 120),
    );
    _bounceAnim = Tween<double>(begin: 1.0, end: 0.88).animate(
      CurvedAnimation(parent: _bounceCtrl, curve: Curves.easeOut),
    );
    _bounceCtrl.addStatusListener((status) {
      if (status == AnimationStatus.completed) _bounceCtrl.reverse();
    });
  }

  @override
  void dispose() {
    _bounceCtrl.dispose();
    super.dispose();
  }

  void _tap() {
    HapticFeedback.mediumImpact();
    _bounceCtrl.forward(from: 0);
    setState(() {
      _count++;
      gPrefs.setInt(_kCount, _count);
    });

    // Vibrate when reaching milestone
    if (_count == _target || _count % _target == 0) {
      HapticFeedback.heavyImpact();
    }
  }

  void _reset() {
    setState(() {
      _count = 0;
      gPrefs.setInt(_kCount, 0);
    });
  }

  void _setTarget(int t) {
    setState(() {
      _target = t;
      gPrefs.setInt(_kTarget, t);
    });
  }

  // Current phrase based on count milestones (0–32 → Subhanallah, etc.)
  String get _currentPhrase {
    if (_target == 33 || _target == 66 || _target == 99) {
      final bucket = (_count ~/ 33).clamp(0, 2);
      return _phrases[bucket];
    }
    return 'ذِكر'; // generic dhikr if target is 100
  }

  // Progress is capped at 100% within each target cycle
  double get _progress {
    if (_target == 0) return 0;
    return (_count % _target) / _target;
  }

  // Completed full cycles
  int get _cycles => _count ~/ _target;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // ── Header ────────────────────────────────────────────────────
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFF1a7a4a), Color(0xFF0f5c38)],
                ),
              ),
              child: Row(
                children: [
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Tasbeeh',
                            style: TextStyle(
                                color: Colors.white,
                                fontSize: 22,
                                fontWeight: FontWeight.bold)),
                        SizedBox(height: 4),
                        Text('Digital Dhikr Counter',
                            style: TextStyle(color: Colors.white70, fontSize: 13)),
                      ],
                    ),
                  ),
                  // Target selector
                  PopupMenuButton<int>(
                    tooltip: 'Set target',
                    icon: const Icon(Icons.tune, color: Colors.white),
                    onSelected: _setTarget,
                    itemBuilder: (_) => _targets
                        .map((t) => PopupMenuItem(
                              value: t,
                              child: Row(
                                children: [
                                  if (t == _target)
                                    const Icon(Icons.check, size: 18,
                                        color: Color(0xFF1a7a4a))
                                  else
                                    const SizedBox(width: 18),
                                  const SizedBox(width: 8),
                                  Text('$t'),
                                ],
                              ),
                            ))
                        .toList(),
                  ),
                ],
              ),
            ),

            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                child: Column(
                  children: [
                    // ── Cycle count ───────────────────────────────────────
                    if (_cycles > 0)
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: const Color(0xFF1a7a4a).withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          'Completed: $_cycles × $_target',
                          style: const TextStyle(
                              color: Color(0xFF1a7a4a),
                              fontWeight: FontWeight.w600,
                              fontSize: 13),
                        ),
                      ),

                    const Spacer(),

                    // ── Big circular counter button ────────────────────────
                    GestureDetector(
                      onTap: _tap,
                      child: AnimatedBuilder(
                        animation: _bounceAnim,
                        builder: (_, child) => Transform.scale(
                          scale: _bounceAnim.value,
                          child: child,
                        ),
                        child: SizedBox(
                          width: 230,
                          height: 230,
                          child: Stack(
                            alignment: Alignment.center,
                            children: [
                              // Progress arc
                              SizedBox.expand(
                                child: CircularProgressIndicator(
                                  value: _progress,
                                  strokeWidth: 10,
                                  backgroundColor: isDark
                                      ? Colors.white12
                                      : Colors.grey.shade200,
                                  valueColor: const AlwaysStoppedAnimation(
                                      Color(0xFF1a7a4a)),
                                ),
                              ),
                              // Inner circle
                              Container(
                                width: 190,
                                height: 190,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: const Color(0xFF1a7a4a),
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(0xFF1a7a4a)
                                          .withValues(alpha: 0.35),
                                      blurRadius: 24,
                                      offset: const Offset(0, 8),
                                    )
                                  ],
                                ),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Text(
                                      '$_count',
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 60,
                                        fontWeight: FontWeight.bold,
                                        height: 1.1,
                                      ),
                                    ),
                                    Text(
                                      '/ $_target',
                                      style: const TextStyle(
                                          color: Colors.white70, fontSize: 16),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 20),

                    // ── Current phrase label ──────────────────────────────
                    Text(
                      _currentPhrase,
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w600,
                        color: isDark ? Colors.white70 : Colors.black87,
                      ),
                    ),

                    const SizedBox(height: 6),
                    Text(
                      'Tap the circle to count',
                      style: TextStyle(
                          fontSize: 13, color: Colors.grey.shade500),
                    ),

                    const Spacer(),

                    // ── Phrase chips (33/66/99 milestones) ───────────────
                    if (_target == 33 || _target == 66 || _target == 99)
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: List.generate(3, (i) {
                          final milestone = (i + 1) * 33;
                          final reached = _count >= milestone;
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            child: Chip(
                              label: Text(_phrases[i],
                                  style: TextStyle(
                                      fontSize: 11,
                                      color: reached
                                          ? Colors.white
                                          : const Color(0xFF1a7a4a))),
                              backgroundColor: reached
                                  ? const Color(0xFF1a7a4a)
                                  : const Color(0xFF1a7a4a).withValues(alpha: 0.1),
                              side: const BorderSide(
                                  color: Color(0xFF1a7a4a), width: 0.8),
                              padding: const EdgeInsets.symmetric(horizontal: 4),
                            ),
                          );
                        }),
                      ),

                    const SizedBox(height: 20),

                    // ── Reset button ──────────────────────────────────────
                    TextButton.icon(
                      onPressed: () => showDialog(
                        context: context,
                        builder: (_) => AlertDialog(
                          title: const Text('Reset counter?'),
                          content: const Text('This will set the count back to 0.'),
                          actions: [
                            TextButton(
                                onPressed: () => Navigator.pop(context),
                                child: const Text('Cancel')),
                            TextButton(
                                onPressed: () {
                                  Navigator.pop(context);
                                  _reset();
                                },
                                style: TextButton.styleFrom(
                                    foregroundColor: Colors.red),
                                child: const Text('Reset')),
                          ],
                        ),
                      ),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Reset'),
                      style: TextButton.styleFrom(foregroundColor: Colors.red),
                    ),

                    const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
