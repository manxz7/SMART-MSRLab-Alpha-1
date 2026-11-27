import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest.dart' as tz;
import 'package:timezone/timezone.dart' as tz;

// ─────────────────────────────────────────────────────────────────────────────
// NotificationService
//
// This handles scheduling prayer time reminders using the device's alarm system.
//
// HOW IT WORKS:
//   Flutter can't run code 24/7, but the OS can fire a "local notification"
//   at an exact time — like a calendar reminder. That's what we use here.
//
// flutter_local_notifications uses "zonedSchedule" which requires timezone-
// aware DateTime. The timezone package (pulled in automatically) handles that.
//
// We assign each prayer a fixed notification ID (0-5). If we reschedule
// (new day / new location), we cancel old ones and write new ones.
// ─────────────────────────────────────────────────────────────────────────────

final _plugin = FlutterLocalNotificationsPlugin();

// One-time setup — must be called before any notifications are scheduled.
Future<void> initNotifications() async {
  // Tell the timezone package to load all timezone data
  tz.initializeTimeZones();

  // Android settings — '@mipmap/ic_launcher' means use the app icon
  const androidSettings =
      AndroidInitializationSettings('@mipmap/ic_launcher');

  // iOS settings — minta izin notifikasi (alert, badge, bunyi) semasa init
  const iosSettings = DarwinInitializationSettings(
    requestAlertPermission: true,
    requestBadgePermission: true,
    requestSoundPermission: true,
  );

  await _plugin.initialize(
    const InitializationSettings(android: androidSettings, iOS: iosSettings),
  );
}

// Prayer names and their Arabic names shown in the notification
const _prayerNames = ['Fajr', 'Sunrise', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
const _prayerArabic = ['الفجر', 'الشروق', 'الظهر', 'العصر', 'المغرب', 'العشاء'];

// ── Schedule notifications for all prayer times for today ────────────────────
// prayerTimes: list of 6 DateTime objects [fajr, sunrise, dhuhr, asr, maghrib, isha]
// adhanEnabled: from user's saved setting. If false, do nothing.
Future<void> schedulePrayerNotifications({
  required List<DateTime> prayerTimes,
  required bool adhanEnabled,
}) async {
  // Cancel all previously scheduled prayer notifications (IDs 0–5)
  // This prevents duplicates when prayer times are refreshed
  for (int i = 0; i < 6; i++) {
    try {
      await _plugin.cancel(i);
    } catch (_) {
      // Ignore: old cached notifications may have incompatible format
    }
  }

  if (!adhanEnabled) return; // User turned off notifications

  final now = DateTime.now();

  for (int i = 0; i < prayerTimes.length; i++) {
    final prayerTime = prayerTimes[i];

    // Skip prayers that have already passed today
    if (prayerTime.isBefore(now)) continue;

    // Convert to a timezone-aware DateTime using UTC.
    // TZDateTime.from() preserves the exact UTC instant of prayerTime —
    // the notification fires at the same absolute moment regardless of
    // which Location is passed, so UTC avoids needing tz.local to be set.
    final scheduledTime = tz.TZDateTime.from(prayerTime, tz.UTC);

    try {
      await _plugin.zonedSchedule(
        i, // notification ID — unique per prayer
        'It is time for ${_prayerNames[i]}', // title
        '${_prayerArabic[i]} • Prayer time has come', // body
        scheduledTime,
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'prayer_times', // channel ID — must be unique per app
            'Prayer Times', // channel name shown in phone settings
            channelDescription: 'Notifications for each daily prayer time',
            importance: Importance.high,
            priority: Priority.high,
          ),
          iOS: DarwinNotificationDetails(presentSound: true),
        ),
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        uiLocalNotificationDateInterpretation:
            UILocalNotificationDateInterpretation.absoluteTime,
      );
    } catch (_) {
      // Ignore: scheduling may fail if exact alarm permission not granted
    }
  }
}

// ── Cancel all prayer notifications ──────────────────────────────────────────
// Called when notifications are turned off in settings
Future<void> cancelAllPrayerNotifications() async {
  for (int i = 0; i < 6; i++) {
    try {
      await _plugin.cancel(i);
    } catch (_) {
      // Ignore
    }
  }
}
