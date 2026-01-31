import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:phoneblock_shared/phoneblock_shared.dart';
import 'package:webview_flutter/webview_flutter.dart';

final String PHONE_BLOCK_CONNECT_URL = '$basePath/mobile/login.jsp';

class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Connect to PhoneBlock"),),
      body: const LoginWidget(),
    );
  }
}

class LoginWidget extends StatefulWidget {
  const LoginWidget({super.key});

  @override
  State<StatefulWidget> createState() => LoginState();
}

class LoginState extends State<LoginWidget> {
  late final WebViewController _controller;

  @override
  void initState() {
    super.initState();

    _controller =
    WebViewController.fromPlatformCreationParams(
        const PlatformWebViewControllerCreationParams()
    )
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (int progress) {
            debugPrint('WebView is loading (progress : $progress%)');
          },
          onPageStarted: (String url) {
            debugPrint('Page started loading: $url');
          },
          onPageFinished: (String url) {
            debugPrint('Page finished loading: $url');
          },
          onWebResourceError: (WebResourceError error) {
            debugPrint(
                '''
                Page resource error:
                  code: ${error.errorCode}
                  description: ${error.description}
                  errorType: ${error.errorType}
                  isForMainFrame: ${error.isForMainFrame}
                ''');
          },
          onNavigationRequest: (NavigationRequest request) {
            debugPrint('allowing navigation to ${request.url}');
            return NavigationDecision.navigate;
          },
          onHttpError: (HttpResponseError error) {
            debugPrint('Error occurred on page: ${error.response?.statusCode}');
          },
          onUrlChange: (UrlChange change) {
            debugPrint('url change to ${change.url}');
          },
        ),
      )
      ..addJavaScriptChannel('TokenResult',
        onMessageReceived: (JavaScriptMessage message) {
          debugPrint('Received token: ${message.message}');
          Navigator.of(context).pop(message.message);
        },
      )
      ..loadRequest(Uri.parse(PHONE_BLOCK_CONNECT_URL));
  }

  @override
  Widget build(BuildContext context) {
    return WebViewWidget(controller: _controller);
  }
}
