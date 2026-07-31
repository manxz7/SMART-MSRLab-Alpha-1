import 'dart:async';
import 'package:flutter/material.dart';
import 'package:geocoding/geocoding.dart';
import '../services/location_service.dart';

// ─────────────────────────────────────────────────────────────────────────────
// LocationPickerDialog — reusable location search dialog
//
// Features:
//   • Live search-as-you-type with 600ms debounce
//   • Shows multiple results grouped by country flag + name
//   • Reverse-geocodes each result to get proper city/region names
//   • Quick-select Malaysian cities always visible below search
// ─────────────────────────────────────────────────────────────────────────────

/// Show the location picker and return the selected [LocationData], or null if cancelled.
Future<LocationData?> showLocationPicker(BuildContext context) {
  return showDialog<LocationData>(
    context: context,
    builder: (_) => const _LocationPickerDialog(),
  );
}

class _LocationPickerDialog extends StatefulWidget {
  const _LocationPickerDialog();

  @override
  State<_LocationPickerDialog> createState() => _LocationPickerDialogState();
}

class _LocationPickerDialogState extends State<_LocationPickerDialog> {
  final _controller = TextEditingController();
  Timer? _debounce;

  bool _searching = false;
  String? _error;
  List<_SearchResult> _results = [];
  bool _hasSearched = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  // ── Live search triggered after 600ms of no typing ──────────────────────
  void _onTextChanged(String value) {
    _debounce?.cancel();
    final query = value.trim();

    if (query.length < 2) {
      setState(() { _results = []; _error = null; _hasSearched = false; });
      return;
    }

    _debounce = Timer(const Duration(milliseconds: 600), () => _search(query));
  }

  Future<void> _search(String query) async {
    setState(() { _searching = true; _error = null; _results = []; });

    try {
      // geocoding returns up to ~5 matches for a query
      final locs = await locationFromAddress(query)
          .timeout(const Duration(seconds: 8));

      if (locs.isEmpty) {
        setState(() { _error = 'No results for "$query"'; _searching = false; _hasSearched = true; });
        return;
      }

      // Reverse-geocode every result to get proper city + country names
      final results = <_SearchResult>[];
      for (final loc in locs.take(6)) {
        try {
          final marks = await placemarkFromCoordinates(loc.latitude, loc.longitude)
              .timeout(const Duration(seconds: 4));
          if (marks.isNotEmpty) {
            final p = marks.first;
            results.add(_SearchResult(
              city: p.locality ?? p.subAdministrativeArea ?? p.name ?? query,
              region: p.administrativeArea ?? '',
              country: p.country ?? '',
              countryCode: p.isoCountryCode ?? '',
              latitude: loc.latitude,
              longitude: loc.longitude,
            ));
          }
        } catch (_) {
          // If reverse-geocode fails for one result, skip it
        }
      }

      // Remove duplicates (same city+country)
      final seen = <String>{};
      final unique = results.where((r) {
        final key = '${r.city}|${r.country}';
        return seen.add(key);
      }).toList();

      setState(() {
        _results = unique;
        _searching = false;
        _hasSearched = true;
        if (unique.isEmpty) _error = 'No results for "$query"';
      });
    } catch (_) {
      setState(() {
        _error = 'Search failed. Check your internet connection.';
        _searching = false;
        _hasSearched = true;
      });
    }
  }

  void _select(LocationData loc) => Navigator.of(context).pop(loc);

  @override
  Widget build(BuildContext context) {
    final cities = LocationService.malaysianCities;

    return AlertDialog(
      title: const Text('Choose your location'),
      contentPadding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      actionsPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      content: SizedBox(
        width: double.maxFinite,
        height: 500,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [

            // ── Search field ──────────────────────────────────────────
            TextField(
              controller: _controller,
              autofocus: true,
              onChanged: _onTextChanged,
              decoration: InputDecoration(
                hintText: 'Search city or country...',
                prefixIcon: const Icon(Icons.search, color: Color(0xFF1a7a4a)),
                suffixIcon: _controller.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _controller.clear();
                          setState(() { _results = []; _error = null; _hasSearched = false; });
                        },
                      )
                    : null,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: Color(0xFF1a7a4a)),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),

            // ── Progress bar ──────────────────────────────────────────
            if (_searching)
              const Padding(
                padding: EdgeInsets.only(top: 6),
                child: LinearProgressIndicator(color: Color(0xFF1a7a4a)),
              )
            else
              const SizedBox(height: 6),

            // ── Error message ──────────────────────────────────────────
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Text(_error!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 12)),
              ),

            // ── Search results ─────────────────────────────────────────
            if (_results.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Results', style: TextStyle(
                fontSize: 11, fontWeight: FontWeight.bold,
                color: const Color(0xFF1a7a4a), letterSpacing: 1.1,
              )),
              const SizedBox(height: 4),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: _results.length,
                  itemBuilder: (_, i) {
                    final r = _results[i];
                    return ListTile(
                      dense: true,
                      leading: Text(
                        _countryFlag(r.countryCode),
                        style: const TextStyle(fontSize: 22),
                      ),
                      title: Text(r.city, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
                      subtitle: Text(
                        [if (r.region.isNotEmpty) r.region, if (r.country.isNotEmpty) r.country].join(', '),
                        style: const TextStyle(fontSize: 12),
                      ),
                      onTap: () => _select(LocationData(
                        latitude: r.latitude,
                        longitude: r.longitude,
                        city: r.city,
                        region: r.region,
                        country: r.countryCode,
                      )),
                    );
                  },
                ),
              ),
              const Divider(),
            ],

            // ── Quick select: Malaysia ─────────────────────────────────
            if (!_hasSearched || _results.isEmpty) ...[
              const SizedBox(height: 4),
              const Text('🇲🇾  Quick select — Malaysia',
                  style: TextStyle(
                    fontSize: 11, fontWeight: FontWeight.bold,
                    color: Color(0xFF1a7a4a), letterSpacing: 1.1,
                  )),
              const SizedBox(height: 4),
              Expanded(
                child: ListView(
                  children: cities.entries.map((entry) => ListTile(
                    dense: true,
                    leading: const Icon(Icons.location_city, color: Color(0xFF1a7a4a), size: 20),
                    title: Text(entry.key, style: const TextStyle(fontSize: 14)),
                    subtitle: Text(entry.value.region, style: const TextStyle(fontSize: 12)),
                    onTap: () => _select(entry.value),
                  )).toList(),
                ),
              ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
      ],
    );
  }

  // Convert ISO country code to flag emoji (e.g. "AU" → 🇦🇺)
  String _countryFlag(String code) {
    if (code.length != 2) return '🌍';
    final base = 0x1F1E6 - 0x41; // regional indicator A offset
    return String.fromCharCodes(code.toUpperCase().codeUnits.map((c) => base + c));
  }
}

class _SearchResult {
  final String city;
  final String region;
  final String country;
  final String countryCode;
  final double latitude;
  final double longitude;

  const _SearchResult({
    required this.city,
    required this.region,
    required this.country,
    required this.countryCode,
    required this.latitude,
    required this.longitude,
  });
}
