import 'dart:async';
import 'dart:math';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:geocoding/geocoding.dart';
import 'package:flutter_compass/flutter_compass.dart';

// ─────────────────────────────────────────────────────────────────────────────
// LocationData — a simple data class to hold everything about the user's location
// ─────────────────────────────────────────────────────────────────────────────
class LocationData {
  final double latitude;
  final double longitude;
  final String city;
  final String region;
  final String country;

  const LocationData({
    required this.latitude,
    required this.longitude,
    required this.city,
    required this.region,
    required this.country,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// LocationService — handles GPS permission and getting coordinates
// ─────────────────────────────────────────────────────────────────────────────
class LocationService {
  static const Map<String, LocationData> malaysianCities = {
    'Kuala Lumpur / Putrajaya': LocationData(latitude: 3.1412, longitude: 101.6865, city: 'Kuala Lumpur', region: 'Wilayah Persekutuan', country: 'MY'),
    'Selangor (Shah Alam)':     LocationData(latitude: 3.0738, longitude: 101.5183, city: 'Shah Alam',    region: 'Selangor',              country: 'MY'),
    'Johor Bahru':              LocationData(latitude: 1.4927, longitude: 103.7414, city: 'Johor Bahru',  region: 'Johor',                 country: 'MY'),
    'Penang (Georgetown)':      LocationData(latitude: 5.4164, longitude: 100.3327, city: 'Georgetown',   region: 'Pulau Pinang',          country: 'MY'),
    'Ipoh (Perak)':             LocationData(latitude: 4.5975, longitude: 101.0901, city: 'Ipoh',         region: 'Perak',                 country: 'MY'),
    'Kota Bharu (Kelantan)':    LocationData(latitude: 6.1254, longitude: 102.2386, city: 'Kota Bharu',   region: 'Kelantan',              country: 'MY'),
    'Kuching (Sarawak)':        LocationData(latitude: 1.5533, longitude: 110.3592, city: 'Kuching',      region: 'Sarawak',               country: 'MY'),
    'Kota Kinabalu (Sabah)':    LocationData(latitude: 5.9804, longitude: 116.0735, city: 'Kota Kinabalu',region: 'Sabah',                 country: 'MY'),
    'Alor Setar (Kedah)':       LocationData(latitude: 6.1248, longitude: 100.3678, city: 'Alor Setar',   region: 'Kedah',                 country: 'MY'),
    'Seremban (N. Sembilan)':   LocationData(latitude: 2.7297, longitude: 101.9381, city: 'Seremban',     region: 'Negeri Sembilan',       country: 'MY'),
    'Melaka':                   LocationData(latitude: 2.1896, longitude: 102.2501, city: 'Melaka',       region: 'Melaka',                country: 'MY'),
    'Kuantan (Pahang)':         LocationData(latitude: 3.8077, longitude: 103.3260, city: 'Kuantan',      region: 'Pahang',                country: 'MY'),
    'Kuala Terengganu':         LocationData(latitude: 5.3302, longitude: 103.1408, city: 'Kuala Terengganu', region: 'Terengganu',        country: 'MY'),
    'Kangar (Perlis)':          LocationData(latitude: 6.4414, longitude: 100.1987, city: 'Kangar',       region: 'Perlis',                country: 'MY'),
  };

  static Future<LocationData> getLocation() async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) throw 'Location services are disabled.';

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) throw 'Location permission denied.';
    }
    if (permission == LocationPermission.deniedForever) throw 'Permission permanently denied.';

    try {
      final Position position = await Geolocator.getCurrentPosition(
        locationSettings: AndroidSettings(
          accuracy: LocationAccuracy.low,
          timeLimit: const Duration(seconds: 15),
        ),
      );
      return _fromPosition(position);
    } catch (e) {
      final lastKnown = await Geolocator.getLastKnownPosition();
      if (lastKnown != null) return _fromPosition(lastKnown);
      throw 'GPS timed out. Are you indoors? Try "Choose location manually" below.';
    }
  }

  static LocationData _fromPosition(Position pos) => LocationData(
        latitude: pos.latitude,
        longitude: pos.longitude,
        city: '', region: '', country: '',
      );

  static Future<void> geocodeInBackground(LocationData location, void Function(LocationData updated) onResult) async {
    try {
      final placemarks = await placemarkFromCoordinates(location.latitude, location.longitude);
      if (placemarks.isNotEmpty) {
        final p = placemarks.first;
        onResult(LocationData(
          latitude: location.latitude,
          longitude: location.longitude,
          city: p.locality ?? p.subAdministrativeArea ?? '',
          region: p.administrativeArea ?? '',
          country: p.isoCountryCode ?? '',
        ));
      }
    } catch (_) {}
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// QiblaService — accurate Qibla compass using Android's hardware sensor fusion
// ─────────────────────────────────────────────────────────────────────────────
class QiblaService {
  static const double _meccaLat = 21.4225;
  static const double _meccaLng = 39.8262;
  static const _geoChannel = MethodChannel('smart.app/geomagnetic');

  static double _toRad(double deg) => deg * pi / 180;

  /// Returns magnetic declination in degrees for the given location.
  /// Positive = East declination, negative = West declination.
  /// Uses Android's built-in GeomagneticField (IGRF model). Returns 0.0 on error.
  static Future<double> getMagneticDeclination(double lat, double lon) async {
    try {
      final declination = await _geoChannel.invokeMethod<double>('getDeclination', {
        'lat': lat,
        'lon': lon,
        'altitude': 0.0,
        'time': DateTime.now().millisecondsSinceEpoch,
      });
      return declination ?? 0.0;
    } catch (_) {
      return 0.0;
    }
  }

  static double calculateQiblaAngle(double userLat, double userLng) {
    final lat1 = _toRad(userLat);
    final lat2 = _toRad(_meccaLat);
    final deltaLng = _toRad(_meccaLng - userLng);
    final x = sin(deltaLng) * cos(lat2);
    final y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng);
    final bearing = atan2(x, y) * 180 / pi;
    return (bearing + 360) % 360;
  }

  static Stream<List<double>> startCompass(double userLat, double userLng) {
    final qiblaAngle = calculateQiblaAngle(userLat, userLng);
    StreamSubscription? compassSub;
    late StreamController<List<double>> controller;

    controller = StreamController<List<double>>(
      onCancel: () => compassSub?.cancel(),
    );

    () async {
      try {
        Stream<CompassEvent>? events;
        try {
          events = FlutterCompass.events;
        } on PlatformException catch (e) {
          if (!controller.isClosed) {
            controller.addError('No compass sensor on this device.\n(${e.message})');
            controller.close();
          }
          return;
        }

        compassSub = events!.listen(
          (e) {
            if (e.heading == null || controller.isClosed) return;
            final rawHeading = e.heading!;
            final needleRotation = (qiblaAngle - rawHeading + 360) % 360;
            final accuracy = e.accuracy ?? 999.0;
            controller.add([rawHeading, qiblaAngle, needleRotation, accuracy]);
          },
          onError: (Object err) {
            if (!controller.isClosed) {
              final msg = err is PlatformException
                  ? 'No compass sensor on this device.\n(${err.message})'
                  : 'Compass error: $err';
              controller.addError(msg);
              controller.close();
            }
          },
          onDone: () {
            if (!controller.isClosed) controller.close();
          },
          cancelOnError: true,
        );
      } catch (e) {
        if (!controller.isClosed) {
          controller.addError('Compass plugin error: $e');
          controller.close();
        }
      }
    }().catchError((Object e) {
      if (!controller.isClosed) {
        controller.addError('Compass plugin error: $e');
        controller.close();
      }
    });

    return controller.stream;
  }
}
