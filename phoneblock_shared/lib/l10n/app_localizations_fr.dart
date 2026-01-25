// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock répondeur téléphonique';

  @override
  String get yourAnswerbots => 'Ton répondeur';

  @override
  String get loginRequired => 'Inscription nécessaire';

  @override
  String get login => 'Connexion';

  @override
  String get loadingData => 'Chargement des données...';

  @override
  String get refreshingData => 'Mettre à jour les données...';

  @override
  String get noAnswerbotsYet =>
      'Tu n\'as pas encore de répondeur, clique sur le bouton plus ci-dessous pour créer un répondeur PhoneBlock.';

  @override
  String get createAnswerbot => 'Créer un répondeur téléphonique';

  @override
  String answerbotName(String userName) {
    return 'Répondeur $userName';
  }

  @override
  String answerbotStats(int newCalls, int talkTimeSeconds, int callsAccepted) {
    return '$newCalls nouveaux appels, $callsAccepted appels, $talkTimeSeconds s temps de conversation total';
  }

  @override
  String get statusActive => 'actif';

  @override
  String get statusConnecting => 'relie...';

  @override
  String get statusDisabled => 'désactivé';

  @override
  String get statusIncomplete => 'incomplet';

  @override
  String get deleteAnswerbot => 'Effacer le répondeur';

  @override
  String get enabled => 'Activé';

  @override
  String get minVotes => 'Minimum de voix';

  @override
  String get minVotesDescription =>
      'Combien de voix au moins un numéro doit-il avoir pour que le répondeur l\'accepte ?';

  @override
  String get minVotes2 => '2 - bloquer immédiatement';

  @override
  String get minVotes4 => '4 - Attendre la confirmation';

  @override
  String get minVotes10 => '10 - seulement si sûr';

  @override
  String get minVotes100 => '100 - seulement les meilleurs spammeurs';

  @override
  String get cannotChangeWhileEnabled =>
      'Ne peut être modifié que si le répondeur est désactivé.';

  @override
  String get saveSettings => 'Enregistrer les paramètres';

  @override
  String get retentionPeriod => 'Durée de conservation';

  @override
  String get retentionPeriodDescription =>
      'Combien de temps les appels doivent-ils être conservés ?';

  @override
  String get retentionNever => 'Ne jamais supprimer';

  @override
  String get retentionWeek => 'Supprimer après 1 semaine';

  @override
  String get retentionMonth => 'Supprimer après 1 mois';

  @override
  String get retentionQuarter => 'Supprimer après 3 mois';

  @override
  String get retentionYear => 'Supprimer après 1 an';

  @override
  String get saveRetentionSettings =>
      'Enregistrer les paramètres de conservation';

  @override
  String get showHelp => 'Afficher l\'aide';

  @override
  String get newAnswerbot => 'Nouveau répondeur';

  @override
  String get usePhoneBlockDynDns => 'Utiliser PhoneBlock-DynDNS';

  @override
  String get dynDnsDescription =>
      'PhoneBlock doit connaître l\'adresse Internet de ta Fritz!Box pour pouvoir prendre des appels.';

  @override
  String get setupPhoneBlockDynDns => 'Configurer PhoneBlock-DynDNS';

  @override
  String get domainName => 'Nom de domaine';

  @override
  String get domainNameHint =>
      'Indique le nom de domaine de ta Fritz!Box. Si ta Fritz!Box n\'a pas encore de nom de domaine, active PhoneBlock-DynDNS.';

  @override
  String get checkDomainName => 'Vérifier les noms de domaine';

  @override
  String get setupDynDns => 'Configurer DynDNS';

  @override
  String get dynDnsInstructions =>
      'Dans les paramètres de ta Fritz!Box, ouvre la page Internet > Partages > DynDNS et saisis les valeurs suivantes :';

  @override
  String get checkDynDns => 'Vérifier le DynDNS';

  @override
  String get createAnswerbotTitle => 'Créer un répondeur téléphonique';

  @override
  String get registerAnswerbot => 'Enregistrer un répondeur téléphonique';

  @override
  String get answerbotRegistered => 'Répondeur enregistré';

  @override
  String get close => 'Fermer';

  @override
  String get error => 'Erreur';

  @override
  String cannotLoadInfo(String message, int statusCode) {
    return 'Impossible de récupérer les informations (erreur $statusCode) : $message';
  }

  @override
  String wrongContentType(String contentType) {
    return 'Les informations ne peuvent pas être récupérées (Content-Type : $contentType).';
  }

  @override
  String get callList => 'Liste d\'appels';

  @override
  String get clearCallList => 'Effacer le journal des appels';

  @override
  String get noCalls => 'Pas encore d\'appels';
}
