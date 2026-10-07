import 'dart:math' as math;

import 'package:flutter/material.dart';

/// 화면 공통 색.
class Palette {
  static const green = Color(0xFF3FA55B);
  static const amber = Color(0xFFB8962E);
  static const red = Color(0xFFD23B3B);
  static const claude = Color(0xFFD97757);
  static const codex = Color(0xFF6B6BF0);

  static bool dark(BuildContext c) => Theme.of(c).brightness == Brightness.dark;
  static Color bg(BuildContext c) =>
      dark(c) ? const Color(0xFF111113) : const Color(0xFFF3F3F1);
  static Color card(BuildContext c) =>
      dark(c) ? const Color(0xFF1C1C1F) : Colors.white;
  static Color text(BuildContext c) =>
      dark(c) ? const Color(0xFFEDEDED) : const Color(0xFF111111);
  static Color sub(BuildContext c) =>
      dark(c) ? const Color(0xFF9A9A9F) : const Color(0xFF8A8A8A);
  static Color track(BuildContext c) =>
      dark(c) ? const Color(0xFF2E2E33) : const Color(0xFFECECEA);
  static Color chip(BuildContext c) =>
      dark(c) ? const Color(0xFF2A2A2E) : const Color(0xFFF1F1EF);
  static Color line(BuildContext c) =>
      dark(c) ? const Color(0xFF2A2A2E) : const Color(0xFFEDEDEB);

  /// 남은 양에 따른 막대 색.
  static Color forRemaining(int remaining) => remaining > 50
      ? green
      : remaining >= 20
      ? amber
      : red;
}

/// 둥근 흰 카드.
class AppCard extends StatelessWidget {
  const AppCard({super.key, required this.child, this.padding});

  final Widget child;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(bottom: 14),
    padding: padding ?? const EdgeInsets.fromLTRB(20, 18, 20, 18),
    decoration: BoxDecoration(
      color: Palette.card(context),
      borderRadius: BorderRadius.circular(24),
    ),
    child: child,
  );
}

/// 둥근 막대.
class LimitBar extends StatelessWidget {
  const LimitBar({super.key, required this.value, required this.color});

  final double value;
  final Color color;

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(6),
    child: LinearProgressIndicator(
      value: value.clamp(0, 1),
      minHeight: 7,
      color: color,
      backgroundColor: Palette.track(context),
    ),
  );
}

/// 서비스 아이콘 (Claude: 주황 별 모양, Codex: 보라 원 안의 >_ ).
class ServiceIcon extends StatelessWidget {
  const ServiceIcon(this.id, {super.key, this.size = 22});

  final String id;
  final double size;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: CustomPaint(painter: _IconPainter(id)),
  );
}

class _IconPainter extends CustomPainter {
  _IconPainter(this.id);

  final String id;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    final c = Offset(s / 2, s / 2);
    if (id == 'claude') {
      final p = Paint()
        ..color = Palette.claude
        ..strokeWidth = s * 0.11
        ..strokeCap = StrokeCap.round;
      for (var i = 0; i < 6; i++) {
        final a = i * math.pi / 6;
        final d = Offset(math.cos(a), math.sin(a)) * (s * 0.44);
        canvas.drawLine(c - d, c + d, p);
      }
    } else {
      final rect = Offset.zero & size;
      canvas.drawCircle(
        c,
        s / 2,
        Paint()
          ..shader = const LinearGradient(
            colors: [Color(0xFF8C7CFF), Color(0xFF4F63E8)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ).createShader(rect),
      );
      final p = Paint()
        ..color = Colors.white
        ..strokeWidth = s * 0.09
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke;
      final path = Path()
        ..moveTo(s * 0.30, s * 0.36)
        ..lineTo(s * 0.44, s * 0.50)
        ..lineTo(s * 0.30, s * 0.64);
      canvas.drawPath(path, p);
      canvas.drawLine(
        Offset(s * 0.50, s * 0.66),
        Offset(s * 0.70, s * 0.66),
        p,
      );
    }
  }

  @override
  bool shouldRepaint(_IconPainter old) => old.id != id;
}

/// 페이지 큰 제목 + 오른쪽 보조 문구.
class PageHeader extends StatelessWidget {
  const PageHeader({super.key, required this.title, this.trailing});

  final String title;
  final String? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 8, 4, 18),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(
          title,
          style: TextStyle(
            fontSize: 34,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: Palette.text(context),
          ),
        ),
        const Spacer(),
        if (trailing != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Text(
              trailing!,
              style: TextStyle(fontSize: 14, color: Palette.sub(context)),
            ),
          ),
      ],
    ),
  );
}

/// 섹션 제목 (설정 화면 등).
class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(8, 10, 8, 10),
    child: Text(
      text,
      style: TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.6,
        color: Palette.sub(context),
      ),
    ),
  );
}
