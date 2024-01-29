import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:phoneblock_answerbot_ui/Debug.dart';
import 'package:phoneblock_answerbot_ui/ErrorDialog.dart';
import 'package:phoneblock_answerbot_ui/InfoField.dart';
import 'package:phoneblock_answerbot_ui/proto.dart';
import 'package:phoneblock_answerbot_ui/sendRequest.dart';
import 'package:phoneblock_answerbot_ui/switchIcon.dart';
import 'package:sn_progress_dialog/progress_dialog.dart';
import 'package:url_launcher/url_launcher.dart';

class BotSetupForm extends StatefulWidget {
  final CreateAnswerbotResponse creation;

  const BotSetupForm(this.creation, {super.key});

  @override
  State<StatefulWidget> createState() {
    return BotSetupState();
  }
}

enum SetupState {
  domainSetup,
  dynDnsSetup,
  enableSip,
  finish
}

RegExp hostNamePattern = RegExp(r'^([a-zA-Z0-9]|[a-zA-Z0-9][-a-zA-Z0-9]{0,61}[a-zA-Z0-9])(\.([a-zA-Z0-9]|[a-zA-Z0-9][-a-zA-Z0-9]{0,61}[a-zA-Z0-9]))*$');

class BotSetupState extends State<BotSetupForm> {
  final _formKey = GlobalKey<FormState>();

  SetupState state = SetupState.domainSetup;
  bool phoneblockDns = false;
  final _hostName = TextEditingController();

  SetupDynDnsResponse? dynDns;

  @override
  Widget build(BuildContext context) {
    switch (state) {
      case SetupState.domainSetup:
        return domainSetup(context);
      case SetupState.dynDnsSetup:
        return dynDnsSetup(context);
      case SetupState.enableSip:
        return enableSip(context);
      case SetupState.finish:
        return sipFinish(context);
      default: return showError(context);
    }
  }

  Widget domainSetup(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter einrichten"),
      ),
      body: Form(
        key: _formKey,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    const Expanded(
                        child: Text("PhoneBlock-DynDNS benutzen")
                    ),
                    Switch(
                      thumbIcon: switchIcon,
                      value: phoneblockDns,
                      onChanged: (bool value) {
                        setState(() {
                          phoneblockDns = value;
                        });
                      },
                    )
                  ],
                ),
              ),

              hintText("PhoneBlock muss die Internet-Adresse Deiner "
                  "Fritz!Box kennen, um den Anrufbeantworter an Deiner "
                  "Fritz!Box anmelden zu können. Wenn Du schon "
                  "MyFRITZ! oder einen anderen DynDNS-Anbieter eingerichtet hast, "
                  "kannst Du diesen Domain-Namen verwenden. Wenn nicht kannst Du ganz einfach "
                  "DynDNS von PhoneBlock einrichten, aktiviere dann diesen Schalter."),

              if (phoneblockDns) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Richte PhoneBlock DynDNS ein.')),
                        );

                        http.Response response = await sendRequest(SetupDynDns()..id=widget.creation.id);
                        if (!context.mounted) return;

                        if (response.statusCode != 200) {
                          return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "DynDNS kann nicht eingerichtet werden: ${response.body}");
                        }

                        setState(() {
                          dynDns = SetupDynDnsResponse.fromString(response.body);
                          state = SetupState.dynDnsSetup;
                        });
                      },
                      child:
                      const Text("PhoneBlock-DynDNS einrichten")
                  ),
                )
              ]
              else ...[
                TextFormField(
                  decoration: const InputDecoration(
                      labelText: 'Domainname',
                      hintText: "Domainname Deiner Fritz!Box (entweder MyFRITZ!-Adresse, oder DynDNS Domainname)"
                  ),
                  controller: _hostName,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return "Eingabe darf nicht leer sein.";
                    }
                    if (!hostNamePattern.hasMatch(value)) {
                      return "Kein gültiger Domain-Name.";
                    }
                    if (value.length > 255) {
                      return "Der Domain-Name ist zu lang.";
                    }
                    return null;
                  },
                  onTapOutside: (evt) => _formKey.currentState!.validate(),
                ),
                hintText("Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS. "
                    "Den Domain-Namen Deiner Deiner Fritz!Box findest Du unter (Unter Internet > Freigaben > DynDNS). "
                    "Alternativ kannst Du auch die MyFRITZ!-Adresse angeben (Internet > MyFRITZ!-Konto), "
                    "z.B. z4z...l4n.myfritz.net.",
                  helpUrl: "setup-ab/help/01-existing-dyndns.png"
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        if (_formKey.currentState!.validate()) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Überprüfe Domainnamen.')),
                          );

                          http.Response response = await sendRequest(
                              EnterHostName()
                                ..id=widget.creation.id
                                ..hostName=_hostName.text);
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "Domainname wurde nicht akzeptiert: ${response.body}");
                          }

                          setState(() {
                            state = SetupState.enableSip;
                          });
                        }
                      },
                      child:
                      const Text("Domainnamen überprüfen")),
                )
              ]
            ],
          ),
        ),
      ),
    );
  }

  Widget dynDnsSetup(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("DynDNS einrichten"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
              children: <Widget>[
                hintText("Öffne in Deinen Fritz!Box-Einstellungen die Seite die "
                    "Internet > Freigaben > DynDNS und trage die hier angegebenen Informationen ein.",
                    helpUrl: "setup-ab/help/02-configure-dyndns.png"
                ),

                const InfoField('Update-URL',
                    "https://phoneblock.net/phoneblock/api/dynip?user=<username>&passwd=<passwd>&ip4=<ipaddr>&ip6=<ip6addr>",
                    key: Key("dynip.updateurl"),
                    help: "Die URL, die Deine Fritz!Box aufruft, um PhoneBlock ihre Internetadresse bekannt zu geben. "
                        "Gib die URL genau so ein, wie sie hier geschrieben ist. Ersetze nicht die Werte in den spitzen "
                        "Klammern, das macht Deine Fritz!Box beim Aufruf automatisch. Nutze am besten die Kopierfuntion, "
                        "um die Werte zu übernehmen."),
                InfoField('Domainname', "${dynDns!.dyndnsUser}.box.phoneblock.net",
                    key: const Key("dynip.domainname"),
                    help: "Dieser Domainname kann später nicht öffentlich aufgelöst werden. Deine Internetadresse wird "
                        "ausschließlich mit PhoneBlock geteilt."),
                InfoField('Benutzername', dynDns!.dyndnsUser,
                    key: const Key("dynip.username"),
                    help: "Der Benutzername, mit dem sich Deine Fritz!Box bei "
                        "PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben."),
                InfoField('Kennwort', dynDns!.dyndnsPassword,
                    key: const Key("dynip.password"),
                    help: "Das Kennwort, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, "
                        "um ihre Internetadresse bekannt zu geben. Aus Sicherheitsgründen kannst Du kein eigenes Kennwort "
                        "eingeben, sondern musst das von PhoneBlock sicher generierte Kennwort verwenden. "),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Überprüfe DynDNS Einrichtung.')),
                        );

                        http.Response response = await sendRequest(
                            CheckDynDns()
                              ..id=widget.creation.id
                        );
                        if (!context.mounted) return;

                        if (response.statusCode != 200) {
                          return showErrorDialog(context, response, 'Einrichtung Fehlgeschlagen', "DynDNS nicht aktuell: ${response.body}");
                        }

                        setState(() {
                          state = SetupState.enableSip;
                        });
                      },
                      child: const Text("DynDNS überprüfen")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget enableSip(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter erstellen"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
              children: <Widget>[
                Text(
                  "Richte jetzt den PhoneBlock-Anrufbeantworter als \"Telefon (mit und ohne Anrufbeantworter)\" ein. "
                      "Damit das auch klappt, halte Dich bitte genau an die folgenden Schritte:"),
                hintText(
                    "1. Öffne in Deinen Fritz!Box-Einstellungen die Seite die "
                        "Telefonie > Telefoniegeräte und klicke auf den Knopf \"Neues Gerät einrichten\".",
                  helpUrl: "setup-ab/help/03-create-new-device.png"
                ),
                hintText(
                    "2. Wähle die Option \"Telefon (mit und ohne Anrufbeantworter)\" und klicke auf \"Weiter\".",
                    helpUrl: "setup-ab/help/04-create-phone.png"
                ),
                hintText(
                    "3. Wähle die Option \"LAN/WLAN (IP-Telefon)\", gib dem Telefon den Namen \"PhoneBlock\" und klicke auf \"Weiter\".",
                    helpUrl: "setup-ab/help/05-choose-ip-phone.png"
                ),
                hintText(
                    "4. Vergib jetzt den folgenden Benutzernamen und das Kennwort für deinen Anrufbeantworter und klicke dann auf \"Weiter\".",
                    helpUrl: "setup-ab/help/06-user-name-and-password.png"
                ),

                InfoField('Benutzername', widget.creation.userName,
                    key: const Key("sip.username"),
                    help: "Der Benutzername, mit dem sich der PhoneBlock-Anrufbeantworter an Deiner Fritz!Box anmeldet."),
                InfoField('Kennwort', widget.creation.password,
                    key: const Key("sip.password"),
                    help: "Das Kennwort, das der PhoneBlock-Anrufbeantworter "
                        "nutzt, um sich an Deiner Fritz!Box anzumelden. "
                        "PhoneBlock hat für Dich ein sicheres Kennwort generiert.  "),
                hintText(
                    "5. Die jetzt abgefragte Rufnummer ist egal, der PhoneBlock-Anrufbeantworter führt "
                        "aktiv keine Gespräche, sondern nimmt nur SPAM-Anrufe entgegen. Die Rufnummer wird "
                        "in Schritt 9 wieder abgewählt. Klicke hier einfach auf \"Weiter\".",
                    helpUrl: "setup-ab/help/07-choose-local-number.png"
                ),
                hintText(
                    "6. Wähle \"alle Anrufe annehmen\" und klicke auf \"Weiter\". Der PhoneBlock-Anrufbeantworter nimmt "
                        "sowieso nur Gespräche an, wenn die Nummer des Anrufers auf der Blockliste steht. Gleichzeitig "
                        "nimmt PhoneBlock nie Gespräche von Nummern an, die in Deinem normalen Telefonbuch stehen.",
                    helpUrl: "setup-ab/help/08-numbers-to-protect.png"
                ),
                hintText(
                    "7. Du siehst eine Zusammenfassung. Die Einstellungen sind (fast) fertig, klicke auf \"Übernehmen\".",
                    helpUrl: "setup-ab/help/10-check-settings.png"
                ),
                hintText(
                    "8. In der Liste der Telefoniegeräte siehst Du jetzt \"PhoneBlock\". Es fehlen noch ein paar "
                        "Einstellungen, die man erst nachträglich machen kann. Klicke daher auf den Bearbeiten-Stift "
                        "in der Zeile des PhoneBlock-Anrufbeantworters.",
                    helpUrl: "setup-ab/help/09-edit-phone.png"
                ),
                hintText(
                    "9. In dem Feld \"Ausgehende Anrufe\" wähle die letzte (leere) Option, da PhoneBlock nie ausgehende Anrufe tätigt "
                        "und daher der Anrufbeantworter keine Nummer für ausgehende Anrufe benötigt.",
                    helpUrl: "setup-ab/help/11-prevent-call.png"
                ),
                hintText(
                    "10. Selektiere den Reiter \"Anmeldedaten\". Bestätige dabei die Rückfage mit Klick auf \"Übernehmen\". "
                        "Wähle jetzt die Option \"Anmeldung aus dem Internet erlauben\", "
                        "damit sich der PhoneBlock-Anrufbeantworter aus der PhoneBlock-Cloud an Deiner Fritz!Box anmelden kann. "
                        "Du musst das Kennwort des Anrufbeantworters (siehe oben) nocheinmal in das "
                        "Feld \"Kennwort\" eingeben, bevor Du auf \"Übernehmen\" klickst. Lösche hierzu vorher "
                        "die im Feld befindlichen Sternchen. ",
                    helpUrl: "setup-ab/help/13-allow-internet-access.png"
                ),
                hintText(
                    "11. Es erscheint eine Nachricht, die vor darauf hinweist, dass über den Internetzugriff kostenpflichtige Verbindungen aufgebaut werden könnten. "
                        "Du kannst Das getrost bestätigen, da erstens PhoneBlock nie aktiv Verbindungen aufbaut, zweitens PhoneBlock für Dich ein sicheres Passwort erzeugt"
                        "hat (siehe oben), so dass sich niemand anderes verbinden kann und drittens Du in Schritt 9 die ausgehenden Verbindungen deaktiviert hast. "
                        "Je nach Einstellungen Deiner Fritz!Box musst Du die Einstellung noch an einem direkt an der Fritz!Box angeschlossenen DECT-Telefon bestätigen."),
                hintText(
                    "12. Jetzt ist alles erledigt. Klicke auf Zurück, um wieder in die Liste der Telefoniegeräte zu springen. Du kannst jetzt mit dem Knopf unten "
                        "Deinen Anrufbeantworter aktivieren."),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        const int maxCount = 20;
                        ProgressDialog pd = ProgressDialog(context: context);
                        pd.show(max: maxCount, msg: 'Versuche Anrufbeantworter anzumelden...');

                        {
                          http.Response response = await sendRequest(
                              EnableAnswerBot()
                                ..id=widget.creation.id
                          );
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            pd.close();

                            return showErrorDialog(context, response, 'Anmeldung des Anrufbeantworters fehlgeschlagen',
                                "Registrierung fehlgeschlagen: ${response.body}");
                          }
                        }

                        int sleep = 2500;
                        int n = 0;
                        while (true) {
                          http.Response response = await sendRequest(
                              CheckAnswerBot()
                                ..id=widget.creation.id
                          );
                          if (!context.mounted) return;

                          var responseCode = response.statusCode;
                          if (responseCode == 200) {
                            break;
                          } else {
                            var errorMessage = response.body;
                            if (responseCode != 409 || n++ == maxCount) {
                              pd.close();

                              return showErrorDialog(context, response, 'Anmeldung des Anrufbeantworters fehlgeschlagen',
                                  "Registrierung fehlgeschlagen: $errorMessage");
                            } else {
                              await Future.delayed(Duration(milliseconds: sleep));

                              pd.update(value: n, msg: "$errorMessage Versuche erneut...");
                            }
                          }
                        }

                        pd.close();
                        setState(() {
                          state = SetupState.finish;
                        });
                      },
                      child: const Text("Anrufbeantworter anmelden")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget sipFinish(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Anrufbeantworter angemeldet"),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                const Text(
                    "Dein PhoneBlock-Anrufbeantworter ist erfolgreich angemeldet. "
                        "Die nächsten Spam-Anrufer können sich jetzt ausgibig mit "
                        "PhoneBlock unterhalten. Wenn Du den PhoneBlock-Anrufbeantworter selber testen möchtest,"
                        "dann wähle die interne Rufnummer des von Dir eingerichteten Telefoniegerätes \"PhoneBlock\". "
                        "Die interne Nummer beginnt i.d.R. mit \"**\"."),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      child: const Text("Schließen")),
                )
              ]
          ),
        ),
      ),
    );
  }

  Widget showError(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: const Text("Fehler"),
        ),
        body: const Text("")
    );
  }

  hintText(String hint, {String? helpUrl}) {
    return             Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child:Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: helpUrl == null ?
              const Icon(Icons.info_outline) :
              IconButton(onPressed: () {
                  launchUrl(Uri.parse('$basePath/$helpUrl'));
                },
                icon: const Icon(Icons.help_outline)),
          ),
          Expanded(child:
          Text(hint),
          ),
        ],
      )
    );
  }

}
