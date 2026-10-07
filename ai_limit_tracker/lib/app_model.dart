import 'package:flutter/foundation.dart';

import 'models.dart';
import 'storage.dart';

/// 앱 전체 상태. 바꿀 때마다 저장(→ 위젯 갱신·알람 재설정)하고
/// 주간 잔여 기준 알림이 필요하면 띄운다.
class AppModel extends ChangeNotifier {
  TrackerState? _state;

  TrackerState? get state => _state;

  Future<void> load() async {
    _state = await Storage.load();
    notifyListeners();
  }

  void update(void Function(TrackerState s, DateTime now) change) {
    final s = _state;
    if (s == null) return;
    final now = DateTime.now();
    change(s, now);
    for (final (svc, rem) in s.takeThresholdAlerts(now)) {
      Storage.notify(
        'threshold_${svc.id}',
        '${svc.name} 주간 한도 $rem% 남음',
        '설정한 알림 기준(${s.settings.alertFor(svc.id).threshold}%) 이하로 떨어졌어요.',
      );
    }
    notifyListeners();
    Storage.save(s);
  }

  /// 시간이 흘러 표시만 바뀔 때(저장 불필요).
  void tick() => notifyListeners();
}
