import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../api/base_path.dart'
  if (dart.library.html) '../api/base_path_web.dart';
import '../api/Api.dart';
import './AnswerBotView.dart';
import '../widgets/ErrorDialog.dart';
import '../widgets/InfoField.dart';
import '../widgets/TitleRow.dart';
import '../models/proto.dart';
import '../api/sendRequest.dart';
import '../widgets/switchIcon.dart';
import 'package:sn_progress_dialog/progress_dialog.dart';
import 'package:url_launcher/url_launcher.dart';
import '../l10n_extensions.dart';

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
        title: TitleRow(context.answerbotL10n.newAnswerbot),
        actions: [
          PopupMenuButton(
            itemBuilder: (BuildContext context) => [
              PopupMenuItem(
                  value: "delete",
                  child: Row(
                    children: [
                      const Padding(padding: EdgeInsets.only(right: 16),
                        child: Icon(Icons.delete_forever, color: Colors.black),
                      ),
                      Text(context.answerbotL10n.deleteAnswerbot)
                    ],
                  ))
            ],
            onSelected: (value) {
              AnswerBotViewState.deleteAnswerBot(context, AnswerbotInfo(id: widget.creation.id, userName: widget.creation.userName));
            },
          )
        ],
      ),
      body: Form(
        key: _formKey,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    Expanded(
                        child: Text(context.answerbotL10n.usePhoneBlockDynDns)
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

              hintText(context.answerbotL10n.dynDnsDescriptionLong),

              if (phoneblockDns) ...[
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(context.answerbotL10n.setupPhoneBlockDynDnsSnackbar)),
                        );

                        http.Response response = await sendRequest(SetupDynDns()..id=widget.creation.id);
                        if (!context.mounted) return;

                        if (response.statusCode != 200) {
                          return showErrorDialog(context, response, context.answerbotL10n.setupFailed, context.answerbotL10n.cannotSetupDynDns(response.body));
                        }

                        setState(() {
                          dynDns = SetupDynDnsResponse.fromString(response.body);
                          state = SetupState.dynDnsSetup;
                        });
                      },
                      child:
                      Text(context.answerbotL10n.setupPhoneBlockDynDns)
                  ),
                )
              ]
              else ...[
                TextFormField(
                  decoration: InputDecoration(
                      labelText: context.answerbotL10n.domainname,
                      hintText: context.answerbotL10n.domainNameHintLong
                  ),
                  controller: _hostName,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return context.answerbotL10n.inputCannotBeEmpty;
                    }
                    if (!hostNamePattern.hasMatch(value)) {
                      return context.answerbotL10n.invalidDomainName;
                    }
                    if (value.length > 255) {
                      return context.answerbotL10n.domainNameTooLong;
                    }
                    return null;
                  },
                  onTapOutside: (evt) => _formKey.currentState!.validate(),
                ),
                hintText(context.answerbotL10n.domainNameHintExtended,
                  helpUrl: "setup-ab/help/01-existing-dyndns.png"
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        if (_formKey.currentState!.validate()) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(context.answerbotL10n.checkingDomainName)),
                          );

                          http.Response response = await sendRequest(
                              EnterHostName()
                                ..id=widget.creation.id
                                ..hostName=_hostName.text);
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            return showErrorDialog(context, response, context.answerbotL10n.setupFailed, context.answerbotL10n.domainNameNotAccepted(response.body));
                          }

                          setState(() {
                            state = SetupState.enableSip;
                          });
                        }
                      },
                      child:
                      Text(context.answerbotL10n.checkDomainName)),
                )
              ]
            ],
          ),
        ),
      ),
    );
  }

  // Note: Extracted as constant to prevent triggering GitGuardian.
  static const pwdPlaceholder = "<passwd>";

  Widget dynDnsSetup(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: TitleRow(context.answerbotL10n.setupDynDns),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
              children: <Widget>[
                hintText(context.answerbotL10n.dynDnsInstructionsLong,
                    helpUrl: "setup-ab/help/02-configure-dyndns.png"
                ),

                InfoField(context.answerbotL10n.updateUrl,
                    "$basePath/api/dynip?user=<username>&passwd=$pwdPlaceholder&ip4=<ipaddr>&ip6=<ip6addr>",
                    key: const Key("dynip.updateurl"),
                    help: context.answerbotL10n.updateUrlHelp2),
                InfoField(context.answerbotL10n.domainname, "${dynDns!.dyndnsDomain}",
                    key: const Key("dynip.domainname"),
                    help: context.answerbotL10n.domainNameHelp2),
                InfoField(context.answerbotL10n.username, dynDns!.dyndnsUser,
                    key: const Key("dynip.username"),
                    help: context.answerbotL10n.usernameHelp),
                InfoField(context.answerbotL10n.passwordLabel, dynDns!.dyndnsPassword,
                    key: const Key("dynip.password"),
                    help: context.answerbotL10n.passwordHelp2),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(context.answerbotL10n.checkingDynDns)),
                        );

                        http.Response response = await sendRequest(
                            CheckDynDns()
                              ..id=widget.creation.id
                        );
                        if (!context.mounted) return;

                        if (response.statusCode != 200) {
                          return showErrorDialog(context, response, context.answerbotL10n.notRegistered, context.answerbotL10n.fritzBoxNotRegistered(response.body));
                        }

                        setState(() {
                          state = SetupState.enableSip;
                        });
                      },
                      child: Text(context.answerbotL10n.checkDynDns)),
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
        title: TitleRow(context.answerbotL10n.createAnswerbotTitle),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: ListView(
              children: <Widget>[
                Text(
                  context.answerbotL10n.sipSetupInstructions),
                hintText(
                    context.answerbotL10n.sipSetupStep1,
                  helpUrl: "setup-ab/help/03-create-new-device.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep2,
                    helpUrl: "setup-ab/help/04-create-phone.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep3,
                    helpUrl: "setup-ab/help/05-choose-ip-phone.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep4,
                    helpUrl: "setup-ab/help/06-user-name-and-password.png"
                ),

                InfoField(context.answerbotL10n.username, widget.creation.userName,
                    key: const Key("sip.username"),
                    help: context.answerbotL10n.usernameHelp2),
                InfoField(context.answerbotL10n.passwordLabel, widget.creation.password,
                    key: const Key("sip.password"),
                    help: context.answerbotL10n.passwordHelp3),
                hintText(
                    context.answerbotL10n.sipSetupStep5,
                    helpUrl: "setup-ab/help/07-choose-local-number.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep6,
                    helpUrl: "setup-ab/help/08-numbers-to-protect.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep7,
                    helpUrl: "setup-ab/help/10-check-settings.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep8,
                    helpUrl: "setup-ab/help/09-edit-phone.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep9,
                    helpUrl: "setup-ab/help/11-prevent-call.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep10,
                    helpUrl: "setup-ab/help/13-allow-internet-access.png"
                ),
                hintText(
                    context.answerbotL10n.sipSetupStep11),
                hintText(
                    context.answerbotL10n.sipSetupStep12),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () async {
                        const int maxCount = 20;
                        ProgressDialog pd = ProgressDialog(context: context);
                        pd.show(max: maxCount, msg: context.answerbotL10n.tryingToRegisterAnswerbot);

                        {
                          http.Response response = await sendRequest(
                              EnableAnswerBot()
                                ..id=widget.creation.id
                          );
                          if (!context.mounted) return;

                          if (response.statusCode != 200) {
                            pd.close();

                            return showErrorDialog(context, response, context.answerbotL10n.answerbotRegistrationFailed,
                                context.answerbotL10n.registrationFailed(response.body));
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

                              return showErrorDialog(context, response, context.answerbotL10n.answerbotRegistrationFailed,
                                  context.answerbotL10n.registrationFailed(errorMessage));
                            } else {
                              await Future.delayed(Duration(milliseconds: sleep));

                              pd.update(value: n, msg: context.answerbotL10n.retryingMessage(errorMessage));
                            }
                          }
                        }

                        pd.close();
                        setState(() {
                          state = SetupState.finish;
                        });
                      },
                      child: Text(context.answerbotL10n.registerAnswerbot)),
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
        title: Text(context.answerbotL10n.answerbotRegistered),
      ),
      body: Form(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                    context.answerbotL10n.answerbotRegisteredSuccess),

                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: ElevatedButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      child: Text(context.answerbotL10n.close)),
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
          title: Text(context.answerbotL10n.error),
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
