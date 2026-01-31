// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Anrufbeantworter';

  @override
  String get yourAnswerbots => 'Deine Anrufbeantworter';

  @override
  String get loginRequired => 'Anmeldung erforderlich';

  @override
  String get login => 'Login';

  @override
  String get loadingData => 'Lade Daten...';

  @override
  String get refreshingData => 'Aktualisiere Daten...';

  @override
  String get noAnswerbotsYet =>
      'Du hast noch keinen Anrufbeantworter, klicke den Plus-Knopf unten, um einen PhoneBlock-Anrufbeantworter anzulegen.';

  @override
  String get createAnswerbot => 'Anrufbeantworter anlegen';

  @override
  String answerbotName(String userName) {
    return 'Anrufbeantworter $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls neue Anrufe, $callsAccepted Anrufe, $talkTimeSeconds s Gesprächszeit gesamt';
  }

  @override
  String get statusActive => 'aktiv';

  @override
  String get statusConnecting => 'verbinde...';

  @override
  String get statusDisabled => 'ausgeschaltet';

  @override
  String get statusIncomplete => 'unvollständig';

  @override
  String get deleteAnswerbot => 'Anrufbeantworter löschen';

  @override
  String get enabled => 'Aktiviert';

  @override
  String get minVotes => 'Mindest-Stimmen';

  @override
  String get minVotesDescription =>
      'Wie viele Stimmen muss eine Nummer mindestens haben, damit sie vom Anrufbeantworter angenommen wird?';

  @override
  String get minVotes2 => '2 - sofort sperren';

  @override
  String get minVotes4 => '4 - Bestätigung abwarten';

  @override
  String get minVotes10 => '10 - erst wenn sicher';

  @override
  String get minVotes100 => '100 - nur Top-Spammer';

  @override
  String get cannotChangeWhileEnabled =>
      'Kann nur bei ausgeschaltetem Anrufbeantworter geändert werden.';

  @override
  String get saveSettings => 'Einstellungen speichern';

  @override
  String get retentionPeriod => 'Aufbewahrungszeit';

  @override
  String get retentionPeriodDescription =>
      'Wie lange sollen Anrufe aufbewahrt werden?';

  @override
  String get retentionNever => 'Niemals löschen';

  @override
  String get retentionWeek => 'Nach 1 Woche löschen';

  @override
  String get retentionMonth => 'Nach 1 Monat löschen';

  @override
  String get retentionQuarter => 'Nach 3 Monaten löschen';

  @override
  String get retentionYear => 'Nach 1 Jahr löschen';

  @override
  String get saveRetentionSettings => 'Aufbewahrungseinstellungen speichern';

  @override
  String get showHelp => 'Hilfe anzeigen';

  @override
  String get newAnswerbot => 'Neuer Anrufbeantworter';

  @override
  String get usePhoneBlockDynDns => 'PhoneBlock-DynDNS benutzen';

  @override
  String get dynDnsDescription =>
      'PhoneBlock muss die Internet-Adresse Deiner Fritz!Box kennen, um Anrufe annehmen zu können.';

  @override
  String get setupPhoneBlockDynDns => 'PhoneBlock-DynDNS einrichten';

  @override
  String get domainName => 'Domain-Name';

  @override
  String get domainNameHint =>
      'Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Domainnamen überprüfen';

  @override
  String get setupDynDns => 'DynDNS einrichten';

  @override
  String get dynDnsInstructions =>
      'Öffne in Deinen Fritz!Box-Einstellungen die Seite die Seite Internet > Freigaben > DynDNS und trage dort die folgenden Werte ein:';

  @override
  String get checkDynDns => 'DynDNS überprüfen';

  @override
  String get createAnswerbotTitle => 'Anrufbeantworter erstellen';

  @override
  String get registerAnswerbot => 'Anrufbeantworter anmelden';

  @override
  String get answerbotRegistered => 'Anrufbeantworter angemeldet';

  @override
  String get close => 'Schließen';

  @override
  String get error => 'Fehler';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Informationen können nicht abgerufen werden (Fehler $statusCode): $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Informationen können nicht abgerufen werden (Content-Type: $contentType).';
  }

  @override
  String get callList => 'Anrufliste';

  @override
  String get clearCallList => 'Anrufliste löschen';

  @override
  String get noCalls => 'Noch keine Anrufe';

  @override
  String get answerbot => 'Anrufbeantworter';

  @override
  String get answerbotSettings => 'Anrufbeantworter-Einstellungen';

  @override
  String get minConfidence => 'Mindestkonfidenz';

  @override
  String get minConfidenceHelp =>
      'Wie viele Beschwerden notwendig sind, damit eine Nummer durch den Anrufbeantworter abgefangen wird.';

  @override
  String get blockNumberRanges => 'Nummernbereiche sperren';

  @override
  String get blockNumberRangesHelp =>
      'Nimmt das Gespräch auch für einen Nummer an, die selbst noch nicht als SPAM bekannt ist, wenn die Vermutung naheliegt, dass die Nummer zu einem Anlagenanschluss gehört, von dem SPAM ausgeht.';

  @override
  String get preferIPv4 => 'IPv4 Kommunikation bevorzugen';

  @override
  String get preferIPv4Help =>
      'Wenn verfügbar wird die Kommunikation mit dem Anrufbeantworter über IPv4 abgewickelt. Es scheint Telefonanschlüsse zu geben, bei denen eine Sprachverbindung über IPv6 nicht möglich ist, obwohl eine IPv6 Adresse verfügbar ist.';

  @override
  String get callRetention => 'Anruf-Aufbewahrung';

  @override
  String get automaticDeletion => 'Automatische Löschung';

  @override
  String get automaticDeletionHelp =>
      'Nach welcher Zeit sollen alte Anrufprotokolle automatisch gelöscht werden? \'Niemals löschen\' deaktiviert die automatische Löschung.';

  @override
  String get dnsSettings => 'DNS Settings';

  @override
  String get dnsSetting => 'DNS-Einstellung';

  @override
  String get phoneBlockDns => 'PhoneBlock-DNS';

  @override
  String get otherProviderOrDomain => 'Anderer Anbieter oder Domainname';

  @override
  String get dnsSettingHelp =>
      'Wie der Anrufbeantworter Deine Fritz!Box im Internet findet.';

  @override
  String get updateUrl => 'Update-URL';

  @override
  String get updateUrlHelp =>
      'Nutzername für die DynDNS-Freige in Deiner Fritz!Box.';

  @override
  String get domainNameHelp => 'Name den Deine Fritz!Box im Internet erhält.';

  @override
  String get dyndnsUsername => 'DynDNS-Benutzername';

  @override
  String get dyndnsUsernameHelp =>
      'Nutzername für die DynDNS-Freige in Deiner Fritz!Box.';

  @override
  String get dyndnsPassword => 'DynDNS-Kennwort';

  @override
  String get dyndnsPasswordHelp =>
      'Das Kennwort, dass Du für die DynDNS-Freigabe verwenden musst.';

  @override
  String get host => 'Host';

  @override
  String get hostHelp =>
      'Der Host-Name, über den Deine Fritz!Box aus dem Internet erreichbar ist.';

  @override
  String get sipSettings => 'SIP Settings';

  @override
  String get user => 'User';

  @override
  String get userHelp =>
      'Der Nutzername, der in der Fritz!Box für den Zugriff auf das Telefoniegerät eingerichtet sein muss.';

  @override
  String get password => 'Password';

  @override
  String get passwordHelp =>
      'Das Passwort, dass für den Zugriff auf das Telefoniegerät in der Fritz!Box vergeben sein muss.';

  @override
  String get savingSettings => 'Speichere Einstellungen...';

  @override
  String get errorSavingSettings => 'Fehler beim Speichern der Einstellungen.';

  @override
  String savingFailed(String message) {
    return 'Speichern fehlgeschlagen: $message';
  }

  @override
  String get enableAfterSavingFailed =>
      'Neu einschalten nach Speichern fehlgeschlagen';

  @override
  String get enablingAnswerbot => 'Schalte Anrufbeantworter ein...';

  @override
  String get errorEnablingAnswerbot =>
      'Fehler beim Einschalten des Anrufbeantworters.';

  @override
  String cannotEnable(String message) {
    return 'Kann nicht einschalten: $message';
  }

  @override
  String get enablingFailed =>
      'Einschalten des Anrufbeantworters fehlgeschlagen';

  @override
  String enablingFailedMessage(String message) {
    return 'Einschalten fehlgeschlagen: $message';
  }

  @override
  String retryingMessage(String message) {
    return '$message Versuche erneut...';
  }

  @override
  String get savingRetentionSettings =>
      'Speichere Aufbewahrungseinstellungen...';

  @override
  String get errorSavingRetentionSettings =>
      'Fehler beim Speichern der Aufbewahrungseinstellungen.';

  @override
  String get automaticDeletionDisabled => 'Automatische Löschung deaktiviert';

  @override
  String retentionSettingsSaved(String period) {
    return 'Aufbewahrungseinstellungen gespeichert ($period)';
  }

  @override
  String get oneWeek => '1 Woche';

  @override
  String get oneMonth => '1 Monat';

  @override
  String get threeMonths => '3 Monate';

  @override
  String get oneYear => '1 Jahr';

  @override
  String get never => 'Niemals';

  @override
  String deleteAnswerbotConfirm(String userName) {
    return 'Soll der Anrufbeantworter $userName wirklich gelöscht werden?';
  }

  @override
  String get cancel => 'Abbrechen';

  @override
  String get delete => 'Löschen';

  @override
  String get deletionFailed => 'Löschen Fehlgeschlagen';

  @override
  String get answerbotCouldNotBeDeleted =>
      'Der Anrufbeantworter konnte nicht gelöscht werden';

  @override
  String get spamCalls => 'SPAM Anrufe';

  @override
  String get deleteCalls => 'Anrufe löschen';

  @override
  String get deletingCallsFailed => 'Löschen fehlgeschlagen';

  @override
  String get deleteRequestFailed =>
      'Die Löschanforderung konnte nicht bearbeitet werden.';

  @override
  String cannotRetrieveCalls(String message, int statusCode) {
    return 'Anrufe können nicht abgerufen werden (Fehler $statusCode): $message';
  }

  @override
  String get noNewCalls => 'Keine neuen Anrufe.';

  @override
  String duration(int seconds) {
    return 'Dauer $seconds s';
  }

  @override
  String today(String time) {
    return 'Heute $time';
  }

  @override
  String yesterday(String time) {
    return 'Gestern $time';
  }

  @override
  String get dynDnsDescriptionLong =>
      'PhoneBlock muss die Internet-Adresse Deiner Fritz!Box kennen, um den Anrufbeantworter an Deiner Fritz!Box anmelden zu können. Wenn Du schon MyFRITZ! oder einen anderen DynDNS-Anbieter eingerichtet hast, kannst Du diesen Domain-Namen verwenden. Wenn nicht kannst Du ganz einfach DynDNS von PhoneBlock einrichten, aktiviere dann diesen Schalter.';

  @override
  String get setupPhoneBlockDynDnsSnackbar => 'Richte PhoneBlock DynDNS ein.';

  @override
  String get setupFailed => 'Einrichtung Fehlgeschlagen';

  @override
  String cannotSetupDynDns(String message) {
    return 'DynDNS kann nicht eingerichtet werden: $message';
  }

  @override
  String get domainname => 'Domainname';

  @override
  String get domainNameHintLong =>
      'Domainname Deiner Fritz!Box (entweder MyFRITZ!-Adresse, oder DynDNS Domainname)';

  @override
  String get inputCannotBeEmpty => 'Eingabe darf nicht leer sein.';

  @override
  String get invalidDomainName => 'Kein gültiger Domain-Name.';

  @override
  String get domainNameTooLong => 'Der Domain-Name ist zu lang.';

  @override
  String get domainNameHintExtended =>
      'Gib den Domain-Namen Deiner Fritz!Box an. Wenn Deine Fritz!Box noch keinen Domain-Namen hat, aktiviere PhoneBlock-DynDNS. Den Domain-Namen Deiner Deiner Fritz!Box findest Du unter (Unter Internet > Freigaben > DynDNS). Alternativ kannst Du auch die MyFRITZ!-Adresse angeben (Internet > MyFRITZ!-Konto), z.B. z4z...l4n.myfritz.net.';

  @override
  String get checkingDomainName => 'Überprüfe Domainnamen.';

  @override
  String domainNameNotAccepted(String message) {
    return 'Domainname wurde nicht akzeptiert: $message';
  }

  @override
  String get dynDnsInstructionsLong =>
      'Öffne in Deinen Fritz!Box-Einstellungen die Seite die Internet > Freigaben > DynDNS und trage die hier angegebenen Informationen ein.';

  @override
  String get updateUrlHelp2 =>
      'Die URL, die Deine Fritz!Box aufruft, um PhoneBlock ihre Internetadresse bekannt zu geben. Gib die URL genau so ein, wie sie hier geschrieben ist. Ersetze nicht die Werte in den spitzen Klammern, das macht Deine Fritz!Box beim Aufruf automatisch. Nutze am besten die Kopierfuntion, um die Werte zu übernehmen.';

  @override
  String get domainNameHelp2 =>
      'Dieser Domainname kann später nicht öffentlich aufgelöst werden. Deine Internetadresse wird ausschließlich mit PhoneBlock geteilt.';

  @override
  String get username => 'Benutzername';

  @override
  String get usernameHelp =>
      'Der Benutzername, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben.';

  @override
  String get passwordLabel => 'Kennwort';

  @override
  String get passwordHelp2 =>
      'Das Kennwort, mit dem sich Deine Fritz!Box bei PhoneBlock anmeldet, um ihre Internetadresse bekannt zu geben. Aus Sicherheitsgründen kannst Du kein eigenes Kennwort eingeben, sondern musst das von PhoneBlock sicher generierte Kennwort verwenden.';

  @override
  String get checkingDynDns => 'Überprüfe DynDNS Einrichtung.';

  @override
  String get notRegistered => 'Nicht registriert';

  @override
  String fritzBoxNotRegistered(String message) {
    return 'Deine Fritz!Box hat sich bei PhoneBlock noch nicht angemeldet, DynDNS ist nicht aktuell: $message';
  }

  @override
  String get sipSetupInstructions =>
      'Richte jetzt den PhoneBlock-Anrufbeantworter als \"Telefon (mit und ohne Anrufbeantworter)\" ein. Damit das auch klappt, halte Dich bitte genau an die folgenden Schritte:';

  @override
  String get sipSetupStep1 =>
      '1. Öffne in Deinen Fritz!Box-Einstellungen die Seite die Telefonie > Telefoniegeräte und klicke auf den Knopf \"Neues Gerät einrichten\".';

  @override
  String get sipSetupStep2 =>
      '2. Wähle die Option \"Telefon (mit und ohne Anrufbeantworter)\" und klicke auf \"Weiter\".';

  @override
  String get sipSetupStep3 =>
      '3. Wähle die Option \"LAN/WLAN (IP-Telefon)\", gib dem Telefon den Namen \"PhoneBlock\" und klicke auf \"Weiter\".';

  @override
  String get sipSetupStep4 =>
      '4. Vergib jetzt den folgenden Benutzernamen und das Kennwort für deinen Anrufbeantworter und klicke dann auf \"Weiter\".';

  @override
  String get usernameHelp2 =>
      'Der Benutzername, mit dem sich der PhoneBlock-Anrufbeantworter an Deiner Fritz!Box anmeldet.';

  @override
  String get passwordHelp3 =>
      'Das Kennwort, das der PhoneBlock-Anrufbeantworter nutzt, um sich an Deiner Fritz!Box anzumelden. PhoneBlock hat für Dich ein sicheres Kennwort generiert.';

  @override
  String get sipSetupStep5 =>
      '5. Die jetzt abgefragte Rufnummer ist egal, der PhoneBlock-Anrufbeantworter führt aktiv keine Gespräche, sondern nimmt nur SPAM-Anrufe entgegen. Die Rufnummer wird in Schritt 9 wieder abgewählt. Klicke hier einfach auf \"Weiter\".';

  @override
  String get sipSetupStep6 =>
      '6. Wähle \"alle Anrufe annehmen\" und klicke auf \"Weiter\". Der PhoneBlock-Anrufbeantworter nimmt sowieso nur Gespräche an, wenn die Nummer des Anrufers auf der Blockliste steht. Gleichzeitig nimmt PhoneBlock nie Gespräche von Nummern an, die in Deinem normalen Telefonbuch stehen.';

  @override
  String get sipSetupStep7 =>
      '7. Du siehst eine Zusammenfassung. Die Einstellungen sind (fast) fertig, klicke auf \"Übernehmen\".';

  @override
  String get sipSetupStep8 =>
      '8. In der Liste der Telefoniegeräte siehst Du jetzt \"PhoneBlock\". Es fehlen noch ein paar Einstellungen, die man erst nachträglich machen kann. Klicke daher auf den Bearbeiten-Stift in der Zeile des PhoneBlock-Anrufbeantworters.';

  @override
  String get sipSetupStep9 =>
      '9. In dem Feld \"Ausgehende Anrufe\" wähle die letzte (leere) Option, da PhoneBlock nie ausgehende Anrufe tätigt und daher der Anrufbeantworter keine Nummer für ausgehende Anrufe benötigt.';

  @override
  String get sipSetupStep10 =>
      '10. Selektiere den Reiter \"Anmeldedaten\". Bestätige dabei die Rückfage mit Klick auf \"Übernehmen\". Wähle jetzt die Option \"Anmeldung aus dem Internet erlauben\", damit sich der PhoneBlock-Anrufbeantworter aus der PhoneBlock-Cloud an Deiner Fritz!Box anmelden kann. Du musst das Kennwort des Anrufbeantworters (siehe oben) nocheinmal in das Feld \"Kennwort\" eingeben, bevor Du auf \"Übernehmen\" klickst. Lösche hierzu vorher die im Feld befindlichen Sternchen.';

  @override
  String get sipSetupStep11 =>
      '11. Es erscheint eine Nachricht, die vor darauf hinweist, dass über den Internetzugriff kostenpflichtige Verbindungen aufgebaut werden könnten. Du kannst Das getrost bestätigen, da erstens PhoneBlock nie aktiv Verbindungen aufbaut, zweitens PhoneBlock für Dich ein sicheres Passwort erzeugthat (siehe oben), so dass sich niemand anderes verbinden kann und drittens Du in Schritt 9 die ausgehenden Verbindungen deaktiviert hast. Je nach Einstellungen Deiner Fritz!Box musst Du die Einstellung noch an einem direkt an der Fritz!Box angeschlossenen DECT-Telefon bestätigen.';

  @override
  String get sipSetupStep12 =>
      '12. Jetzt ist alles erledigt. Klicke auf Zurück, um wieder in die Liste der Telefoniegeräte zu springen. Du kannst jetzt mit dem Knopf unten Deinen Anrufbeantworter aktivieren.';

  @override
  String get tryingToRegisterAnswerbot =>
      'Versuche Anrufbeantworter anzumelden...';

  @override
  String get answerbotRegistrationFailed =>
      'Anmeldung des Anrufbeantworters fehlgeschlagen';

  @override
  String registrationFailed(String message) {
    return 'Registrierung fehlgeschlagen: $message';
  }

  @override
  String get answerbotRegisteredSuccess =>
      'Dein PhoneBlock-Anrufbeantworter ist erfolgreich angemeldet. Die nächsten Spam-Anrufer können sich jetzt ausgibig mit PhoneBlock unterhalten. Wenn Du den PhoneBlock-Anrufbeantworter selber testen möchtest,dann wähle die interne Rufnummer des von Dir eingerichteten Telefoniegerätes \"PhoneBlock\". Die interne Nummer beginnt i.d.R. mit \"**\".';
}
