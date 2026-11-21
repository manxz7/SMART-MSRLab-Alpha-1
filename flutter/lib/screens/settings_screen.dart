import 'package:flutter/material.dart';
import '../main.dart'; // gives us gPrefs — the global SharedPreferences instance

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen — app preferences (calculation method, madhab, about)
// ─────────────────────────────────────────────────────────────────────────────
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _adhanEnabled     = true;
  bool _vibrationEnabled = true;
  String _calcMethod     = 'Singapore';
  String _madhab         = 'Shafi';
  late TextEditingController _apiUrlController;

  static const _kAdhan     = 'adhan_enabled';
  static const _kVibration = 'vibration_enabled';
  static const _kCalc      = 'calc_method';
  static const _kMadhab    = 'madhab';
  static const _kApiUrl    = 'api_url';

  @override
  void initState() {
    super.initState();
    // gPrefs is already loaded — read values instantly, no async needed
    _adhanEnabled     = gPrefs.getBool(_kAdhan)     ?? true;
    _vibrationEnabled = gPrefs.getBool(_kVibration) ?? true;
    _calcMethod       = gPrefs.getString(_kCalc)    ?? 'Singapore';
    _madhab           = gPrefs.getString(_kMadhab)  ?? 'Shafi';
    _apiUrlController = TextEditingController(
      text: gPrefs.getString(_kApiUrl) ?? '',
    );
  }

  @override
  void dispose() {
    _apiUrlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // ── Header ──────────────────────────────────────────────────────
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
              decoration: const BoxDecoration(
                color: Color(0xFF1a7a4a),
                borderRadius:
                    BorderRadius.vertical(bottom: Radius.circular(24)),
              ),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Settings',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.bold)),
                  SizedBox(height: 4),
                  Text('Customise your QiblaApp',
                      style: TextStyle(color: Colors.white70, fontSize: 13)),
                ],
              ),
            ),

            // ── Settings list ────────────────────────────────────────────
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [

                  // ── Notifications section ─────────────────────────────
                  _sectionHeader('Notifications'),
                  _switchTile(
                    icon: Icons.notifications_active,
                    title: 'Adhan Alerts',
                    subtitle: 'Play Adhan at prayer times',
                    value: _adhanEnabled,
                    onChanged: (v) {
                      setState(() => _adhanEnabled = v);
                      gPrefs.setBool(_kAdhan, v); // instant write to storage
                    },
                  ),
                  _switchTile(
                    icon: Icons.vibration,
                    title: 'Vibration',
                    subtitle: 'Vibrate at prayer times',
                    value: _vibrationEnabled,
                    onChanged: (v) {
                      setState(() => _vibrationEnabled = v);
                      gPrefs.setBool(_kVibration, v); // instant write to storage
                    },
                  ),

                  const SizedBox(height: 16),

                  // ── Calculation section ───────────────────────────────
                  _sectionHeader('Prayer Calculation'),
                  _dropdownTile(
                    icon: Icons.calculate_outlined,
                    title: 'Calculation Method',
                    subtitle: 'Used outside Malaysia',
                    value: _calcMethod,
                    options: const [
                      'Singapore',
                      'Muslim World League',
                      'ISNA (North America)',
                      'Umm Al-Qura (Saudi)',
                      'Egyptian',
                      'Karachi',
                    ],
                    onChanged: (v) {
                      setState(() => _calcMethod = v!);
                      gPrefs.setString(_kCalc, v!); // instant write to storage
                    },
                  ),
                  _dropdownTile(
                    icon: Icons.mosque_outlined,
                    title: 'Madhab (Asr)',
                    subtitle: 'Affects Asr prayer time',
                    value: _madhab,
                    options: const ['Shafi', 'Hanafi'],
                    onChanged: (v) {
                      setState(() => _madhab = v!);
                      gPrefs.setString(_kMadhab, v!); // instant write to storage
                    },
                  ),

                  const SizedBox(height: 16),

                  // ── API Server section ────────────────────────────────
                  _sectionHeader('API Server'),
                  Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).cardColor,
                      borderRadius: BorderRadius.circular(12),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.04),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Row(
                          children: [
                            Icon(Icons.cloud_outlined, color: Color(0xFF1a7a4a)),
                            SizedBox(width: 8),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text('Halal Checker Server URL',
                                      style: TextStyle(fontWeight: FontWeight.w500)),
                                  Text(
                                    'Paste your ngrok URL here to use from any network',
                                    style: TextStyle(fontSize: 12, color: Colors.grey),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        TextField(
                          controller: _apiUrlController,
                          decoration: InputDecoration(
                            hintText: 'https://xxxx.ngrok-free.app',
                            border: const OutlineInputBorder(),
                            isDense: true,
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 10),
                            suffixIcon: IconButton(
                              icon: const Icon(Icons.save_outlined,
                                  color: Color(0xFF1a7a4a)),
                              tooltip: 'Save URL',
                              onPressed: () {
                                final url = _apiUrlController.text.trim();
                                if (url.isEmpty) {
                                  gPrefs.remove(_kApiUrl);
                                } else {
                                  gPrefs.setString(_kApiUrl, url);
                                }
                                FocusScope.of(context).unfocus();
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('Server URL saved'),
                                    backgroundColor: Color(0xFF1a7a4a),
                                  ),
                                );
                              },
                            ),
                          ),
                          keyboardType: TextInputType.url,
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Leave empty to use local WiFi (192.168.x.x)',
                          style: TextStyle(fontSize: 11, color: Colors.grey[500]),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 16),

                  // ── About section ─────────────────────────────────────
                  _sectionHeader('About'),
                  _infoTile(
                    icon: Icons.info_outline,
                    title: 'Version',
                    subtitle: '1.0.0',
                  ),
                  _infoTile(
                    icon: Icons.public,
                    title: 'Prayer Data (Malaysia)',
                    subtitle: 'api.waktusolat.app — JAKIM official times',
                  ),
                  _infoTile(
                    icon: Icons.calculate,
                    title: 'Prayer Data (Worldwide)',
                    subtitle: 'Calculated using Muslim World League formula',
                  ),
                  _infoTile(
                    icon: Icons.explore_outlined,
                    title: 'Qibla Bearing',
                    subtitle: 'Great Circle formula toward Mecca (21.4225°N, 39.8262°E)',
                  ),

                  const SizedBox(height: 16),

                  // ── Malaysia info card ────────────────────────────────
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1a7a4a).withOpacity(0.08),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.check_circle_outline,
                            color: Color(0xFF1a7a4a)),
                        SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'In Malaysia: prayer times are fetched directly from '
                            'JAKIM\'s official database via api.waktusolat.app. '
                            'Your GPS zone is auto-detected.',
                            style: TextStyle(
                                fontSize: 12, color: Color(0xFF1a7a4a)),
                          ),
                        ),
                      ],
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

  // ── Section header label ─────────────────────────────────────────────────
  Widget _sectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 4),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          color: Color(0xFF1a7a4a),
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
        ),
      ),
    );
  }

  // ── Toggle switch tile ───────────────────────────────────────────────────
  Widget _switchTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 6,
              offset: const Offset(0, 2))
        ],
      ),
      child: ListTile(
        leading: Icon(icon, color: const Color(0xFF1a7a4a)),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
        subtitle: Text(subtitle,
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
        trailing: Switch(
          value: value,
          onChanged: onChanged,
          activeThumbColor: const Color(0xFF1a7a4a),
        ),
      ),
    );
  }

  // ── Dropdown tile ────────────────────────────────────────────────────────
  Widget _dropdownTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required String value,
    required List<String> options,
    List<String>? labels, // optional: display labels different from values
    required ValueChanged<String?> onChanged,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 6,
              offset: const Offset(0, 2))
        ],
      ),
      child: Row(
        children: [
          Icon(icon, color: const Color(0xFF1a7a4a)),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(fontWeight: FontWeight.w500)),
                Text(subtitle,
                    style:
                        const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          DropdownButton<String>(
            value: value,
            underline: const SizedBox(),
            style: const TextStyle(
                color: Color(0xFF1a7a4a), fontWeight: FontWeight.w600),
            items: options.asMap().entries
                .map((e) => DropdownMenuItem(
                      value: e.value,
                      child: Text(labels != null ? labels[e.key] : e.value),
                    ))
                .toList(),
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }

  // ── Read-only info tile ──────────────────────────────────────────────────
  Widget _infoTile({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 6,
              offset: const Offset(0, 2))
        ],
      ),
      child: ListTile(
        leading: Icon(icon, color: const Color(0xFF1a7a4a)),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
        subtitle: Text(subtitle,
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
      ),
    );
  }
}
