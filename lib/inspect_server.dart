import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart' show rootBundle;

/// 검사 페이지(assets/inspect.html)를 PC 로컬호스트에서 제공하는 작은 HTTP 서버.
///
/// adb reverse 로 폰의 localhost:port 를 이 서버로 터널링해서, 폰 브라우저가
/// http://localhost:port 로 검사 페이지를 엽니다. 인터넷·와이파이·GitHub Pages
/// 없이 USB 만으로 동작하고, localhost 는 브라우저가 '보안 컨텍스트'로 취급해
/// 웹오디오(사이렌)도 정상 재생됩니다.
class InspectServer {
  InspectServer._();
  static final InspectServer instance = InspectServer._();

  HttpServer? _server;
  String? _html;

  /// 서버가 떠 있으면 그 포트를, 아니면 새로 띄우고 포트를 반환.
  Future<int> ensureStarted() async {
    final existing = _server;
    if (existing != null) return existing.port;

    _html ??= await rootBundle.loadString('assets/inspect.html');
    // 127.0.0.1 의 임의 빈 포트에 바인딩 (adb reverse 대상)
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    server.listen((HttpRequest req) async {
      try {
        req.response.headers.contentType =
            ContentType('text', 'html', charset: 'utf-8');
        req.response.headers.set('Cache-Control', 'no-store');
        req.response.write(_html);
      } catch (_) {
      } finally {
        await req.response.close();
      }
    });
    return server.port;
  }
}
