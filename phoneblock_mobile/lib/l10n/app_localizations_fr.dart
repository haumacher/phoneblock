// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Mobile';

  @override
  String get settings => 'Réglages';

  @override
  String get deleteAll => 'Supprimer tout';

  @override
  String get noCallsYet => 'Pas encore d\'appels filtrés';

  @override
  String get noCallsDescription =>
      'PhoneBlock vérifiera automatiquement les appels entrants et bloquera les appels SPAM.';

  @override
  String get blocked => 'Bloque';

  @override
  String get accepted => 'Adopté';

  @override
  String votes(int count) {
    return '$count votes';
  }

  @override
  String get viewOnPhoneBlock => 'Afficher sur PhoneBlock';

  @override
  String get confirmDeleteAll => 'Supprimer tous les appels filtrés ?';

  @override
  String get confirmDeleteAllMessage =>
      'Cette action ne peut pas être annulée.';

  @override
  String get cancel => 'Annuler';

  @override
  String get delete => 'Supprimer';

  @override
  String get settingsTitle => 'Réglages';

  @override
  String get callScreening => 'Filtrage des appels';

  @override
  String get minSpamReports => 'Messages SPAM minimaux';

  @override
  String minSpamReportsDescription(int count) {
    return 'Les numéros sont bloqués à partir de $count messages';
  }

  @override
  String get blockNumberRanges => 'Bloquer des plages de numéros';

  @override
  String get blockNumberRangesDescription =>
      'Bloquer les domaines avec beaucoup de messages SPAM';

  @override
  String get minSpamReportsInRange => 'Messages SPAM minimaux dans le domaine';

  @override
  String minSpamReportsInRangeDescription(int count) {
    return 'Les domaines sont bloqués à partir de $count messages';
  }

  @override
  String get about => 'Sur';

  @override
  String get version => 'Version';

  @override
  String get developer => 'Développeur';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Site web';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Code source';

  @override
  String get sourceCodeLicense => 'Open Source (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock est un projet open-source sans traçage et sans publicité. Le service est financé par des dons.';

  @override
  String get donate => 'Dons';

  @override
  String pendingCallsNotification(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nouveaux appels filtrés',
      one: '1 nouvel appel filtré',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Tapez pour ouvrir l\'application';

  @override
  String get setupWelcome => 'Bienvenue sur PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Autorisations requises';

  @override
  String get grantPermission => 'Donner l\'autorisation';

  @override
  String get continue_ => 'Continuer';

  @override
  String get finish => 'Prêt';

  @override
  String get loginRequired => 'Inscription PhoneBlock';

  @override
  String get loginToPhoneBlock => 'S\'inscrire sur PhoneBlock';

  @override
  String get verifyingLogin => 'Inscription en cours de vérification...';

  @override
  String get loginFailed => 'Échec de la connexion';

  @override
  String get loginSuccess => 'Inscription réussie !';

  @override
  String get reportAsLegitimate => 'Signaler comme légitime';

  @override
  String get reportAsSpam => 'Signaler comme SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Voir sur PhoneBlock';

  @override
  String get deleteCall => 'Supprimer';

  @override
  String get report => 'Signaler';

  @override
  String get notLoggedIn =>
      'Vous n\'êtes pas inscrit. Veuillez vous connecter.';

  @override
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber déclaré comme légitime';
  }

  @override
  String reportError(String error) {
    return 'Erreur lors de la déclaration : $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
    return '$phoneNumber signalé comme SPAM';
  }

  @override
  String get selectSpamCategory => 'Choisir la catégorie de SPAM';

  @override
  String get errorDeletingAllCalls =>
      'Erreur lors de la suppression de tous les appels';

  @override
  String get errorDeletingCall => 'Erreur lors de l\'effacement de l\'appel';

  @override
  String get notLoggedInShort => 'Non inscrit';

  @override
  String get errorOpeningPhoneBlock =>
      'Erreur lors de l\'ouverture de PhoneBlock.';

  @override
  String get permissionNotGranted => 'L\'autorisation n\'a pas été accordée.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Configuration';

  @override
  String get welcome => 'Bienvenue sur';

  @override
  String get connectPhoneBlockAccount => 'Connecter un compte PhoneBlock';

  @override
  String get permissions => 'Autorisations';

  @override
  String get allowCallFiltering => 'Autoriser le filtrage des appels';

  @override
  String get done => 'Prêt';

  @override
  String get setupComplete => 'Mise en place terminée';

  @override
  String get minReportsCount => 'Nombre minimal de messages';

  @override
  String callsBlockedAfterReports(int count) {
    return 'Les appels sont bloqués à partir des messages $count.';
  }

  @override
  String rangesBlockedAfterReports(int count) {
    return 'Les domaines sont bloqués à partir de $count messages';
  }

  @override
  String get welcomeMessage =>
      'Bienvenue sur PhoneBlock Mobile !\n\nCette application vous aide à bloquer automatiquement les appels de spam. Pour cela, vous avez besoin d\'un compte gratuit sur PhoneBlock.net.\n\nConnectez-vous à votre compte PhoneBlock pour continuer :';

  @override
  String get connectToPhoneBlock => 'Se connecter à PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Connecté à PhoneBlock';

  @override
  String get accountConnectedSuccessfully => '✓ Compte connecté avec succès';

  @override
  String get permissionsMessage =>
      'Pour bloquer automatiquement les appels spam, PhoneBlock Mobile a besoin de l\'autorisation de vérifier les appels entrants.\n\nCette autorisation est nécessaire pour que l\'application fonctionne :';

  @override
  String get permissionGranted => 'Autorisation accordée';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Autorisation accordée avec succès';

  @override
  String get setupCompleteMessage =>
      'Installation terminée !\n\nPhoneBlock Mobile est maintenant prêt à bloquer les appels de spam. L\'application vérifie automatiquement les appels entrants et bloque les numéros de spam connus en se basant sur la base de données PhoneBlock.\n\nAppuyez sur \"Terminé\" pour revenir à l\'écran principal.';

  @override
  String get verifyingLoginTitle => 'Vérifier le login';

  @override
  String get loginSuccessMessage => 'Connexion réussie !';

  @override
  String get redirectingToSetup => 'Transfert vers l\'établissement...';

  @override
  String tokenVerificationFailed(String error) {
    return 'Échec de la vérification du jeton : $error';
  }

  @override
  String get backToSetup => 'Retour à l\'établissement';

  @override
  String get tokenBeingVerified => 'Token en cours de vérification...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock n\'a pas pu être ouvert.';

  @override
  String get ratingLegitimate => 'Légitime';

  @override
  String get ratingAdvertising => 'Publicité';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Appel ping';

  @override
  String get ratingGamble => 'Jeu-concours';

  @override
  String get ratingFraud => 'Fraude';

  @override
  String get ratingPoll => 'Sondage';

  @override
  String get noLoginTokenReceived => 'Aucun jeton de connexion reçu.';

  @override
  String get settingSaved => 'Réglage enregistré';

  @override
  String get errorSaving => 'Erreur lors de la sauvegarde';

  @override
  String ratePhoneNumber(String phoneNumber) {
    return 'évaluer $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count messages',
      one: '1 message',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Messages légitimes',
      one: '1 déclaration de légitimité',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Aucun message';

  @override
  String todayTime(String time) {
    return 'Aujourd\'hui, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Hier, $time';
  }

  @override
  String get callHistoryRetention =>
      'Enregistrement de l\'historique des appels';

  @override
  String retentionPeriodDescription(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Conserver les appels $days jours',
      one: 'Conserver les appels 1 jour',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Conserver tous les appels';

  @override
  String retentionDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days jours',
      one: '1 jour',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Illimité';

  @override
  String get addCommentSpam => 'Ajouter un commentaire (facultatif)';

  @override
  String get commentHintSpam =>
      'Pourquoi est-ce du spam ? Quel était le sujet de l\'appel ? Restez courtois.';

  @override
  String get addCommentLegitimate => 'Ajouter un commentaire (facultatif)';

  @override
  String get commentHintLegitimate =>
      'Pourquoi est-ce légitime ? Qui vous a appelé ? Restez courtois.';

  @override
  String get serverSettings => 'Paramètres du serveur';

  @override
  String get serverSettingsDescription =>
      'Gérer les paramètres de votre compte PhoneBlock';

  @override
  String get searchNumber => 'Chercher un numéro';

  @override
  String get searchPhoneNumber => 'Chercher un numéro de téléphone';

  @override
  String get enterPhoneNumber => 'Saisir le numéro de téléphone';

  @override
  String get phoneNumberHint => 'par ex. +49 123 456789';

  @override
  String get search => 'Rechercher';

  @override
  String get invalidPhoneNumber =>
      'Veuillez saisir un numéro de téléphone valide';

  @override
  String get blacklistTitle => 'Liste noire';

  @override
  String get blacklistDescription => 'Numéros que vous avez bloqués';

  @override
  String get whitelistTitle => 'Liste blanche';

  @override
  String get whitelistDescription =>
      'Numéros que vous avez marqués comme légitimes';

  @override
  String get blacklistEmpty => 'Votre liste noire est vide';

  @override
  String get whitelistEmpty => 'Votre liste blanche est vide';

  @override
  String get blacklistEmptyHelp =>
      'Ajoutez des numéros en signalant les appels indésirables comme spam.';

  @override
  String get whitelistEmptyHelp =>
      'Ajoutez des numéros en signalant les appels bloqués comme légitimes.';

  @override
  String get errorLoadingList => 'Erreur lors du chargement de la liste';

  @override
  String get numberRemovedFromList => 'Numéro retiré';

  @override
  String get errorRemovingNumber => 'Erreur lors de la suppression du numéro';

  @override
  String get confirmRemoval => 'Confirmer la suppression';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return 'Supprimer $phone de la liste noire ?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return 'Supprimer $phone de la liste blanche ?';
  }

  @override
  String get remove => 'Supprimer';

  @override
  String get retry => 'Essayer à nouveau';

  @override
  String get editComment => 'Modifier le commentaire';

  @override
  String get commentLabel => 'Commentaire';

  @override
  String get commentHint => 'Ajouter une note à ce numéro';

  @override
  String get save => 'Enregistrer';

  @override
  String get commentUpdated => 'Commentaire mis à jour';

  @override
  String get errorUpdatingComment =>
      'Erreur lors de la mise à jour du commentaire';

  @override
  String get appearance => 'Apparence';

  @override
  String get themeMode => 'Design';

  @override
  String get themeModeDescription => 'Choisir un design clair ou foncé';

  @override
  String get themeModeSystem => 'Système standard';

  @override
  String get themeModeLight => 'Lumineux';

  @override
  String get themeModeDark => 'Sombre';

  @override
  String get experimentalFeatures => 'Fonctions expérimentales';

  @override
  String get answerbotFeature => 'Répondeur téléphonique (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Fonction expérimentale : gérer les répondeurs SPAM qui parlent automatiquement aux appelants spam';
}
