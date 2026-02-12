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
  String get missed => 'Manqué';

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
      other: '$count Plaintes',
      one: '1 plainte',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Plaintes dans le domaine numérique',
      one: '1 plainte dans la zone de numérotation',
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
      'Expérimental : gérer le répondeur SPAM pour la Fritz!Box dans l\'application';

  @override
  String get answerbotMenuTitle => 'Répondeur téléphonique';

  @override
  String get answerbotMenuDescription => 'Gérer les répondeurs SPAM';

  @override
  String potentialSpamLabel(String rating) {
    return 'Suspect : $rating';
  }

  @override
  String get statistics => 'Statistiques';

  @override
  String get blockedCallsCount => 'Appels bloqués';

  @override
  String get suspiciousCallsCount => 'Appels suspects';

  @override
  String get fritzboxTitle => 'Fritz!Box';

  @override
  String get fritzboxConnected => 'Connecté';

  @override
  String get fritzboxConnectedNotProtected => 'Connecté, non protégé';

  @override
  String get fritzboxOffline => 'Pas disponible';

  @override
  String get fritzboxError => 'Erreur de connexion';

  @override
  String get fritzboxNotConfiguredShort => 'Non aménagé';

  @override
  String get fritzboxNotConfigured => 'Pas de Fritz!Box configurée';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Connectez-vous à votre Fritz!Box pour voir les appels de votre ligne fixe.';

  @override
  String get fritzboxConnect => 'Connecter la Fritz!Box';

  @override
  String get fritzboxDisconnect => 'Déconnecter la Fritz!Box';

  @override
  String get fritzboxDisconnectTitle => 'Déconnecter la Fritz!Box ?';

  @override
  String get fritzboxDisconnectMessage =>
      'Les appels et les données d\'accès enregistrés sont effacés.';

  @override
  String get fritzboxSyncNow => 'Synchroniser maintenant';

  @override
  String get fritzboxSyncDescription =>
      'Consulter la liste d\'appels de la Fritz!Box';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nouveaux appels synchronisés',
      one: '1 nouvel appel synchronisé',
      zero: 'Pas de nouveaux appels',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Erreur de synchronisation';

  @override
  String get fritzboxVersion => 'Version du FRITZ!OS';

  @override
  String get fritzboxHost => 'Adresse';

  @override
  String get fritzboxCachedCalls => 'Appels enregistrés';

  @override
  String get fritzboxLastSync => 'Dernière synchronisation';

  @override
  String get fritzboxJustNow => 'A l\'instant';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Avant $count minutes',
      one: 'Il y a 1 minute',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Avant $count heures',
      one: 'Il y a 1 heure',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Connecter la Fritz!Box';

  @override
  String get fritzboxStepDetection => 'Trouver une Fritz!Box';

  @override
  String get fritzboxStepDetectionSubtitle =>
      'Recherche automatique sur le réseau';

  @override
  String get fritzboxStepLogin => 'S\'inscrire';

  @override
  String get fritzboxStepLoginSubtitle => 'Saisir les données d\'accès';

  @override
  String get fritzboxSearching => 'Recherche de Fritz!Box...';

  @override
  String get fritzboxNotFound => 'Fritz!Box introuvable';

  @override
  String get fritzboxNotFoundDescription =>
      'La Fritz!Box n\'a pas pu être trouvée automatiquement. Veuillez saisir l\'adresse manuellement.';

  @override
  String get fritzboxHostLabel => 'Adresse de la Fritz!Box';

  @override
  String get fritzboxRetrySearch => 'Chercher à nouveau';

  @override
  String get fritzboxManualConnect => 'Relier';

  @override
  String get fritzboxLoginDescription =>
      'Saisissez vos données d\'accès Fritz!Box. Vous les trouverez dans l\'interface utilisateur Fritz!Box sous Système > Utilisateurs Fritz!Box.';

  @override
  String get fritzboxShowUsername => 'Saisir le nom d\'utilisateur';

  @override
  String get fritzboxUsernameLabel => 'Nom d\'utilisateur';

  @override
  String get fritzboxPasswordLabel => 'Mot de passe';

  @override
  String get fritzboxCredentialsNote =>
      'Vos données d\'accès sont stockées en toute sécurité sur votre appareil.';

  @override
  String get fritzboxTestAndSave => 'Tester et enregistrer';

  @override
  String get fritzboxConnectionFailed =>
      'Échec de la connexion. Veuillez vérifier les données d\'accès.';

  @override
  String get fritzboxFillAllFields => 'Veuillez remplir tous les champs.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz!Box injoignable - afficher les appels enregistrés';

  @override
  String get sourceMobile => 'Mobile';

  @override
  String get sourceFritzbox => 'Fritz!Box';

  @override
  String get fritzboxStepBlocklist => 'Protection contre le spam';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Configurer une liste de blocage';

  @override
  String get fritzboxBlocklistDescription =>
      'Choisissez comment votre Fritz!Box doit être protégée contre les appels de spam.';

  @override
  String get fritzboxCardDavTitle => 'Liste de blocage CardDAV';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box synchronise la liste de blocage directement avec PhoneBlock. Recommandé pour FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Installer plus tard';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Vous pourrez activer la protection contre le spam plus tard dans les paramètres.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV nécessite FRITZ ! OS 7.20 ou une version plus récente. Votre Fritz!Box a une version plus ancienne.';

  @override
  String get fritzboxFinishSetup => 'Terminer la mise en place';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Veuillez d\'abord vous connecter à PhoneBlock.';

  @override
  String get fritzboxCannotGetUsername =>
      'Le nom d\'utilisateur PhoneBlock n\'a pas pu être récupéré.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'La liste de blocage n\'a pas pu être configurée';

  @override
  String get fritzboxCardDavStatus => 'Statut CardDAV';

  @override
  String get fritzboxCardDavStatusSynced => 'Synchronisé';

  @override
  String get fritzboxCardDavStatusPending => 'Synchronisation en attente';

  @override
  String get fritzboxCardDavStatusError => 'Erreur de synchronisation';

  @override
  String get fritzboxCardDavStatusDisabled => 'Désactivé';

  @override
  String get fritzboxCardDavNote =>
      'La Fritz!Box synchronise l\'annuaire téléphonique une fois par jour à minuit.';

  @override
  String get fritzboxBlocklistMode => 'Mode de protection contre le spam';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (synchronisation automatique)';

  @override
  String get fritzboxBlocklistModeNone => 'Non activé';

  @override
  String get fritzboxEnableCardDav => 'Activer CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Synchroniser la liste de blocage des spams directement avec la Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'Liste de blocage CardDAV activée';

  @override
  String get fritzboxDisableCardDav => 'Désactiver CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => 'Désactiver CardDAV ?';

  @override
  String get fritzboxDisableCardDavMessage =>
      'La liste de blocage CardDAV est supprimée de la Fritz!Box.';

  @override
  String get fritzboxDisable => 'Désactiver';

  @override
  String get fritzboxCardDavDisabled => 'Liste de blocage CardDAV désactivée';
}
