import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:phoneblock_mobile/state.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:http/http.dart' as http;

const String contextPath = kDebugMode ? "/pb-test" : "/phoneblock";
const String pbBaseUrl = 'https://phoneblock.net$contextPath';
const String pbLoginUrl = '$pbBaseUrl/mobile/login';
const String pbApiTest = '$pbBaseUrl/api/test';

void main() {
  runApp(MaterialApp.router(
      routerConfig: router,
      theme: ThemeData(
        appBarTheme: AppBarTheme(
          backgroundColor: const Color.fromARGB(255, 0, 209, 178),
          foregroundColor: Colors.white,
        ),
      ),
  ));
}

final router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) {
        return const TestPhoneBlockMobile();
      },
      routes: [
        GoRoute(
          path: '$contextPath/mobile/response',
          builder: (context, state) {
            var loginToken = state.uri.queryParameters["loginToken"];
            if (kDebugMode) {
              print("Token received (${state.path}): $loginToken");
            }
            if (loginToken == null) {
              return LoginFailed();
            } else {
              return VerifyLogin(loginToken);
            }
          },
        ),
      ],
    ),
  ],
);

class VerifyLogin extends StatefulWidget {
  final String token;

  const VerifyLogin(this.token, {super.key});

  @override
  State<StatefulWidget> createState() => VerifyLoginState();
}

class VerifyLoginState extends State<VerifyLogin> {
  late Future<http.Response> checkResult;

  @override
  void initState() {
    super.initState();

    var token = widget.token;
    setAuthToken(token);

    checkResult = http.get(Uri.parse(pbApiTest),
      headers: {
        "Authorization": "Bearer $token"
      }
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Überprüfe Login'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Center(
            child: FutureBuilder(
              future: checkResult,
              builder: (context, state) {
                if (state.hasData) {
                  return Text("Login successful.");
                } else if (state.hasError) {
                  return Text("Token verification failed: ${state.error}");
                } else {
                  return Text("Verifying token...");
                }
              }),
          ),
        ],
      ),
    );
  }
}

class LoginFailed extends StatelessWidget {
  const LoginFailed({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Login fehlgeschlagen'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Center(
            child: Text("No login token received."),
          ),
        ],
      ),
    );
  }
}

class TestPhoneBlockMobile extends StatelessWidget {
  const TestPhoneBlockMobile({super.key});

  @override
  Widget build(BuildContext context) {
    AppState state = AppState(calls: [
      Call(phone: "0123456789", type: Type.iNCOMING, started: 1, duration: 2),
      Call(phone: "0123456789", type: Type.mISSED),
      Call(phone: "0123456789", type: Type.oUTGOING, started: 1, duration: 2),
      Call(phone: "0123456789", type: Type.bLOCKED, rating: Rating.gAMBLE),
    ]);
    return SetupPage();
  }
}

class SetupPage extends StatefulWidget {
  const SetupPage({super.key});

  @override
  State<StatefulWidget> createState() {
    return SetupState();
  }
}

const platform = MethodChannel('de.haumacher.phoneblock_mobile/call_checker');

class SetupState extends State<SetupPage> {

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("PhoneBlock Mobile: Setup"),),
      body: Center(
        child: Column(
          children: [
            ElevatedButton(
                onPressed: () {
                  registerPhoneBlock(context);
                },
                child: const Text("Mit PhoneBlock verbinden")),
            ElevatedButton(
                onPressed: requestPermission,
                child: const Text("Berechtigung erteilen um Anrufe zu filtern")),
          ])
      ),
    );
  }

  void requestPermission() async {
    bool granted = await platform.invokeMethod("requestPermission");
  }
}

void setAuthToken(String token) {
  platform.invokeMethod("setAuthToken", token);
}

Future<String?> getAuthToken() async {
  return platform.invokeMethod("getAuthToken");
}

void registerPhoneBlock(BuildContext context) async {
  bool ok = await launchUrl(Uri.parse(pbLoginUrl));
  if (!ok) {
    const snackBar = SnackBar(content: Text('Failed to open PhoneBlock.'));
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }
}

class MyHomePage extends StatefulWidget {
  final AppState state;

  const MyHomePage(this.state, {super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  _MyHomePageState();

  @override
  Widget build(BuildContext context) {
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        title: const Text("PhoneBlock mobile"),
        actions: [
          PopupMenuButton(
            icon: const Icon(Icons.menu),
              itemBuilder: (context){
                return [
                  const PopupMenuItem<int>(
                    value: 0,
                    child: Text("Login"),
                  ),

                  const PopupMenuItem<int>(
                    value: 1,
                    child: Text("My Account"),
                  ),

                  const PopupMenuItem<int>(
                    value: 2,
                    child: Text("Settings"),
                  ),

                  const PopupMenuItem<int>(
                    value: 3,
                    child: Text("Logout"),
                  ),
                ];
              },
              onSelected:(value){
                if (value == 0) {
                  if (kDebugMode) {
                    print("Login is selected.");
                  }

                  registerPhoneBlock(context);
                } else if (value == 1){
                  if (kDebugMode) {
                    print("My account menu is selected.");
                  }
                }else if(value == 2){
                  if (kDebugMode) {
                    print("Settings menu is selected.");
                  }
                }else if(value == 3){
                  if (kDebugMode) {
                    print("Logout menu is selected.");
                  }
                }
              }
          ),
      ],
      ),
      body: ListView(
        children: widget.state.calls.map((call) =>
          Dismissible(key: Key(call.phone), child:
            ListTile(
              leading: icon(call.type),
              title : Row(mainAxisSize: MainAxisSize.min, children: [
                hint(call.rating),
                Text(call.label ?? call.phone)
              ]),
              subtitle: duration(call),
              trailing: action(call),
            ),
            onDismissed: (direction) => {debugPrint("Dismissed: ${call.phone}") },
          )
        ).toList(),
      ),
      floatingActionButton: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton(
            onPressed: fetchBlocklist,
            tooltip: 'Update Blocklist',
            child: const Icon(Icons.cloud_download),
          ),
        ],
      ) // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget duration(Call call) {
    if (call.started == 0) {
      return const Text("");
    }
    var date = DateTime.fromMillisecondsSinceEpoch(call.started);

    DateFormat format = createFormat(date);
    return Text(format.format(date));
  }

  DateFormat createFormat(DateTime date) {
    var now = DateTime.now();
    var today = DateTime(now.year, now.month, now.day);
    var yesterday = today.subtract(const Duration(days: 1));
    var thisYear = DateTime(today.year);
    
    DateFormat format;
    if (date.isBefore(thisYear)) {
      format = DateFormat('hh:mm dd.MM.yyyy');
    } else if (date.isBefore(yesterday)) {
      format = DateFormat('hh:mm dd.MM.');
    } else if (date.isBefore(today)) {
      format = DateFormat('hh:mm gestern');
    } else {
      format = DateFormat('hh:mm heute');
    }
    return format;
  }

  Widget action(Call call) {
    switch (call.type) {
      case Type.iNCOMING:
        return IconButton(icon: const Icon(Icons.block, color: Colors.redAccent,),
            onPressed: () => Navigator.push(context,
              MaterialPageRoute(builder: (context) => RateScreen(call)),
            )
        );
      case Type.mISSED:
        return IconButton(
            icon: const Icon(Icons.manage_search, color: Colors.blueAccent,),
            onPressed: () {
              if (kDebugMode) {
                print('Report as spam: ${call.phone}');
              }
            });
      case Type.bLOCKED:
        return IconButton(
            icon: const Icon(Icons.playlist_add, color: Colors.redAccent,),
            onPressed: () {
              if (kDebugMode) {
                print('Record call: ${call.phone}');
              }
            });
      case Type.oUTGOING:
        return const SizedBox.shrink();
    }
  }

  Widget hint(Rating rating) {
    if (rating == Rating.uNKNOWN || rating == Rating.aLEGITIMATE) {
      return const SizedBox.shrink();
    }
    return Padding(padding: const EdgeInsets.only(right: 5),
        child: Container(
          decoration: BoxDecoration(
              color: bgColor(rating),
              borderRadius: const BorderRadius.all(Radius.circular(10))
          ),
          child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              child: label(rating)),
        ));
    }

  Icon icon(Type type) {
    switch (type) {
      case Type.bLOCKED: return const Icon(Icons.phone_disabled, color: Colors.redAccent);
      case Type.oUTGOING: return const Icon(Icons.phone_forwarded, color: Colors.green);
      case Type.mISSED: return const Icon(Icons.phone_missed, color: Colors.blueAccent);
      case Type.iNCOMING: return const Icon(Icons.phone_callback, color: Colors.green);
    }
  }

  void fetchBlocklist() async {
    if (await FlutterContacts.requestPermission()) {
      if (kDebugMode) {
        print("Permission OK");
      }
      var allGroups = await FlutterContacts.getGroups();
      if (kDebugMode) {
        print("Groups: $allGroups");
      }

      List<Group> spamGroups = [];
      for (var group in allGroups) {
        if (group.name == "SPAM") {
          spamGroups.add(group);
        }
      }

      if (kDebugMode) {
        print("SPAM groups: $spamGroups");
      }

      var allContacts = await FlutterContacts.getContacts(withGroups: true);
      var spamContacts = allContacts.where((contact) => containsAny(spamGroups, contact.groups));
      for (var contact in spamContacts) {
        if (kDebugMode) {
          for (var num in contact.phones) {
            print("Found SPAM number: $num");
          }
        }
        await contact.delete();
      }

      while (spamGroups.length > 1) {
        Group duplicate = spamGroups.removeLast();
        await FlutterContacts.deleteGroup(duplicate);
      }

      Group spamGroup = spamGroups.firstOrNull ?? await createSpamGroup();

      var photo = Uint8List.sublistView(await rootBundle.load("assets/images/spam_icon.png"));

      var pb = Contact(
        name: Name(last: "ZZ SPAM"),
        phones: [
          Phone("012345679", label: PhoneLabel.work),
          Phone("0234567891", label: PhoneLabel.work),
        ],
        groups: [spamGroup],
        photo: photo,
      );
      pb = await pb.insert();

      if (kDebugMode) {
        print("Created contact $pb");
      }

      if (kDebugMode) {
        print("Update OK");
      }
    } else {
      if (kDebugMode) {
        print("Permission denied");
      }
    }
  }

  /// Whether [all] contains any element of [some].
  bool containsAny(List all, List some) {
    for (Object x in some) {
      if (all.contains(x)) {
        return true;
      }
    }
    return false;
  }

  Future<Group> createSpamGroup() async {
    Group spamGroup = Group("phoneblock-spam", "SPAM");
    spamGroup = await FlutterContacts.insertGroup(spamGroup);
    if (kDebugMode) {
      print("Created SPAM group: $spamGroup");
    }
    return spamGroup;
  }

}

Widget label(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Text("Legitim", style: TextStyle(color: Colors.white));
    case Rating.aDVERTISING: return const Text("Werbung", style: TextStyle(color: Color.fromRGBO(0,0,0,.7)));
    case Rating.uNKNOWN: return const Text("Anderer Grund", style: TextStyle(color: Colors.white));
    case Rating.pING: return const Text("Ping-Anruf", style: TextStyle(color: Colors.white));
    case Rating.gAMBLE: return const Text("Gewinnspiel", style: TextStyle(color: Colors.white));
    case Rating.fRAUD: return const Text("Betrug", style: TextStyle(color: Colors.white));
    case Rating.pOLL: return const Text("Umfrage", style: TextStyle(color: Colors.white));
  }
}

Color bgColor(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Color.fromRGBO(72, 199, 142, 1);
    case Rating.uNKNOWN: return const Color.fromRGBO(170, 172, 170, 1);
    case Rating.pING: return const Color.fromRGBO(31, 94, 220, 1);
    case Rating.pOLL: return const Color.fromRGBO(157, 31, 220, 1);
    case Rating.aDVERTISING: return const Color.fromRGBO(255, 224, 138, 1);
    case Rating.gAMBLE: return const Color.fromRGBO(241, 122, 70, 1);
    case Rating.fRAUD: return const Color.fromRGBO(241, 70, 104, 1);
  }
}

Icon icon(Rating rating) {
  switch (rating) {
    case Rating.aLEGITIMATE: return const Icon(Icons.check);
    case Rating.uNKNOWN: return const Icon(Icons.question_mark);
    case Rating.pING: return const Icon(Icons.block);
    case Rating.pOLL: return const Icon(Icons.query_stats);
    case Rating.aDVERTISING: return const Icon(Icons.ondemand_video);
    case Rating.gAMBLE: return const Icon(Icons.videogame_asset_off);
    case Rating.fRAUD: return const Icon(Icons.warning);
  }
}

class RateScreen extends StatelessWidget {
  final Call call;

  const RateScreen(this.call, {super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Rate ${call.phone}'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: Rating.values.map((rating) => ratingButton(context, rating, call)).toList()
        )
      ),
    );
  }

  Widget ratingButton(BuildContext context, Rating rating, Call call) {
    return Container(
      margin: const EdgeInsets.all(10),
      child: ElevatedButton(
        style: ElevatedButton.styleFrom(
          backgroundColor: bgColor(rating),
          shadowColor: Colors.blueGrey,
          elevation: 3,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(32.0)),
          minimumSize: const Size(200, 60),
          maximumSize: const Size(200, 60),
        ),
        onPressed: () {
          Navigator.pop(context);
        },
        child: Row(mainAxisSize: MainAxisSize.max, children: [
          Padding(padding: const EdgeInsets.only(right: 10), child: icon(rating)),
          label(rating)
        ]),
      ),
    );
  }

}