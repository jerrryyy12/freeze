/// 안드로이드 센서 타입(android.sensor.*) → 한국어 라벨.
///
/// dumpsys sensorservice 에서 나오는 표준 센서 타입을 모두 한글로 보여주기 위한 표.
/// 목록에 없는(제조사 커스텀) 센서는 영문 타입명을 그대로 표시합니다.
const Map<String, String> kSensorLabels = {
  // 기본 모션 센서
  'accelerometer': '가속도',
  'accelerometer_uncalibrated': '가속도(미보정)',
  'accelerometer_limited_axes': '가속도(제한축)',
  'accelerometer_limited_axes_uncalibrated': '가속도(제한축·미보정)',
  'gyroscope': '자이로',
  'gyroscope_uncalibrated': '자이로(미보정)',
  'gyroscope_limited_axes': '자이로(제한축)',
  'gyroscope_limited_axes_uncalibrated': '자이로(제한축·미보정)',
  'magnetic_field': '지자기',
  'magnetic_field_uncalibrated': '지자기(미보정)',
  'gravity': '중력',
  'linear_acceleration': '선형가속도',

  // 자세/회전
  'rotation_vector': '회전벡터',
  'game_rotation_vector': '게임 회전벡터',
  'geomagnetic_rotation_vector': '지자기 회전벡터',
  'orientation': '방향(레거시)',
  'device_orientation': '기기 방향',
  'pose_6dof': '6자유도 자세',
  'head_tracker': '헤드 트래커',
  'heading': '방위',

  // 환경 센서
  'light': '조도',
  'proximity': '근접',
  'pressure': '기압',
  'ambient_temperature': '주변 온도',
  'relative_humidity': '상대 습도',
  'temperature': '온도',

  // 걸음/움직임 감지
  'step_counter': '걸음수',
  'step_detector': '걸음 감지',
  'significant_motion': '유의미한 움직임',
  'motion_detect': '움직임 감지',
  'stationary_detect': '정지 감지',
  'tilt_detector': '기울임 감지',
  'pick_up_gesture': '들어올림 감지',
  'wake_gesture': '깨우기 제스처',
  'glance_gesture': '흘긋보기 제스처',
  'wrist_tilt_gesture': '손목 기울임 제스처',
  'low_latency_offbody_detect': '착용 감지',

  // 생체/기타
  'heart_rate': '심박',
  'heart_beat': '심박 박동',
  'hinge_angle': '힌지 각도(폴더블)',
  'motion_accel': '모션 가속',
  'dynamic_sensor_meta': '동적 센서 메타',
  'additional_info': '추가 정보',
};

/// 센서 타입에 대한 한글 라벨을 반환. 표에 없으면 영문 타입 그대로.
String sensorLabel(String type) => kSensorLabels[type] ?? type;

/// "라벨 (영문타입)" 형식. 라벨이 없으면 영문타입만.
String sensorDisplay(String type) {
  final label = kSensorLabels[type];
  return label != null ? '$label ($type)' : type;
}
