# Verzeichnis mit Audiodateien, aus denen PhoneBlock einen Dialog mit dem Anrufer zusammensetzt

Ein PhoneBlock-Dialog besteht aus folgenden Komponenten:
 * Eine Begrüßung ("hello")
 * Warten auf eine Meldung des Gegenüber ("waiting")
 * Eine Rückfrage ("question")
 * Warten auf eine Antwort ("waiting")
 * Weiter bei "Rückfrage".
 * Eine Nachfrage, wenn sich das Gegenüber auf die Begrüßung nicht meldet ("who-is-calling")
 * Eine Nachfrage, wenn das Gegenüber auf eine Rückfrage nicht antwortet ("still-there")

Für jeden Dialog-Teil gibt es ein Unterverzeichnis, in den eine Audiodatei (WAV) abgelegt werden muss, in der (am 
besten) viele mögliche Sätze gesprochen werden, die zur jeweiligen Situation des Dialoges passen. Zwischen jedem Satz 
muss sich ausreichend Stille (1s) befinden, damit PhoneBlock die Sätze voneinander trennen kann. Die folgenden 
Abschnitte machen Vorschläge, was man in den jeweiligen Audio-Dateien aufsprechen könnte.

Wenn die Audiodateien vorliegen, muss das Dialogverzeichnis mit dem Tool `ConversionInitializer` initialisiert werden.
Diese Tool splittet die Audiodateien in die einzelnen Sätze und bereite verschiedene Formatversionen vor, die für die 
Kommunikation benötigt werden (je nachdem was für ein Audioformat mit der Gegenstelle ausgehandelt wird).

Die Textfragmente können entweder selbst eingesprochen werden, oder mit einer Text-to-Speech-KI generiert werden 
(z.B. Speach generated with https://elevenlabs.io)

Man legt die Audiodateien (z.B. als mp3) in den jeweiligen Ordnern (`hello`, ...) ab und lässt das 
benötigte Format für die VOIP-Übertragung mit dem Script `../bin/convert-audio.sh` generieren. Dieses füllt dann 
Unterordner `PCMA` und `PCMA-WB` in den jeweiligen Audio-Ordnern.

## Ansagen, mit denen das Gespräch begonnen wird ("hello")
 * Schönen guten Tag!
 * Ja bitte!
 * Mit wem spreche ich?
 * Hallo, guten Tag!
 * Guten Tag!
 * Einen schönen guten Tag!
 * Grüß Gott!
 * Sie wünschen, bitte?
 * Moin moin!
 * Tach!
 * Hi!
 * Guten Tag, mit wem spreche ich?
 * Guten Tag, wer ist da bitte?
 * Guten Tag, was wünschen Sie?

## Nach der Ansage meldet sich das Call-Center nicht sofort, PhoneBlock fragt nach, wer anruft ("who-is-calling")
 * Hallo, wer ist da bitte?
 * Hallo, wer ist in der Leitung?
 * Hallo, ist da jemand?
 * Hallo, bitte melden Sie sich!
 * Ich höre nichts, wer sind Sie?
 * Ist da jemand? Ich höre nichts!

## Typischerweise wird gefragt, ob man das richtige Opfer am Aparat hat, PhoneBlock bestätigt dies ohne ja zu sagen oder Namen zu nennen ("go-ahead")

Diese Gesprächsphase wird aktuell noch nicht verwendet.

 * Haben wir schon mal telefoniert?
 * Schön, dass sie anrufen.
 * Sie sind genau an der richtigen Stelle.
 * Um was geht es genau?
 * Sie sind richtig verbunden.
 * Da bin ich genau der richtige Ansprechpartner.
 * Genau darauf habe ich schon lange gewartet!
 * Wie schön, dass sie sich bei mir melden!
 * Darauf habe ich schon wirklich lange gewartet!
 * Sie sind hier richtig!
 * Sie können mit mir sprechen!
 * Am Apparat!
 * Da sind Sie hier richtig!
 * Ich bin ganz Ohr!
 * Da bin ich gespannt. Legen Sie los!
 * Endlich melden Sie sich. Darauf habe ich schon lange gewartet!
 * Gut von ihnen zu hören!
 * Das freut mich, dass sie anrufen!
 * Danke, dass sie sich melden. Ich bin gespannt!

## Das Gegenüber sagt nichts, PhoneBlock stellt eine Nachfrage ("still-there")

 * Sind sie noch dran?
 * Haben sie etwas gesagt?
 * Ich kann sie nicht hören. Haben Sie etwas gesagt?
 * Entschuldigen Sie, die Leitung ist schlecht. Was haben Sie gerade gesagt?
 * Hallo, haben Sie etwas gesagt?
 * Entschuldigen Sie, aber ich höre nichts. Können Sie mich hören?                            
 * Gibt es ein Problem mit der Verbindung? Haben Sie etwas gesagt?             
 * Ist die Verbindung unterbrochen? Hören Sie mich?            
 * Ich habe Sie nicht verstanden, können Sie das nocheinmal wiederholen?                    
 * Es scheint, als ob es Kommunikationsprobleme gibt. Könnten Sie noch einmal anrufen?                      
 * Tut mir leid, aber ich kann nichts hören. Können Sie bitte wiederholen, was Sie gesagt haben?  
 * Gibt es ein technisches Problem mit der Verbindung? Ich höre Sie nicht!
 * Es sieht so aus, als hätten wir ein Verbindungsproblem. Haben Sie etwas gesagt?                            

## Das Gegenüber hat etwas gesagt, PhoneBlock stellt eine idiotische Rückfrage ("question")

 * Was genau soll ich jetzt machen?
 * Das klingt nach einer guten Gelegenheit.
 * Wie genau gehen wir jetzt weiter vor?
 * Benötigen Sie noch weitere Informationen?
 * Das habe ich noch nicht richtig verstanden. Was muss ich jetzt machen?
 * Können Sie das noch genauer erklären?
 * Das klingt interessant, aber ich habe die Details noch nicht richtig verstanden.
 * Wie muss ich mir das genau vorstellen?
 * Können Sie bitte noch ein paar Hintergrundinformationen liefern?
 * Kann ich mich darauf auch wirklich verlassen?
 * Mich würden noch weitere Beispiele interessieren.
 * Ich bin entzückt über ihren Vorschlag, wie geht es jetzt weiter?
 * Kann ich das auch schriftlich bekommen?
 * Wollen Sie noch mit meinem Mann darüber sprechen?
 * Ich habe Sie nicht ganz verstanden, können Sie das bitte nocheinmal wiederholen?
 * Entschuldigung, können Sie das nocheinmal wiederholen?
 * Das ist prima. Was muss ich jetzt tun?
 * Das freut mich, wie geht es weiter?
 * Wirklich interessant. Kann ich mehr darüber erfahren?
 * Vielleicht muss ich erst mit meinem Mann darüber sprechen. Muss ich das sofort entscheiden?
 * Der Vorschlag ist nicht schlecht. Kann dabei auch nichts schief gehen?
 * Klingt gut. Ist das aber auch wirklich sicher?
 * Guter Vorschlag. Wie machen wir weiter?
 * Gerne dürfen Sie mir mehr dazu sagen.
 * Können Sie das bitte genauer erklären?
 * Können Sie mir mehr Informationen dazu geben?
 * Lassen Sie mich darüber nachdenken. Muss ich das jetzt sofort entscheiden?
 * Können Sie mir Beispiele dafür nennen?
 * Ich verstehe noch nicht ganz, können Sie das nocheinmal erklären?
 * Ich bin mir nicht sicher, können Sie weitere Einzelheiten mitteilen?
 * Können Sie mir bitte mehr Details dazu geben?
 * Könnten Sie mir das bitte genauer erklären?
 * Gibt es weitere Informationen, die dabei relevant sein könnten?
 * Ist das unbedingt notwendig? Welche anderen Optionen können in Betracht gezogen werden?
 * Das kann ich mir nicht vorstellen, könnten Sie das bitte ausführlicher erläutern?
 * Das klingt sehr allgemein. Welche genauen Daten haben Sie hier?
 * Gibt es zusätzliche Fakten, die berücksichtigt werden sollten?
 * Können Sie das genauer in einzelnen Schritten erklären?
 
## Während PhoneBlock auf eine Antwort wartet, wird Rauschen abgespielt, um eine Verbindung vorzutäuschen ("waiting")

 * Eine Audio-Datei, in der einfach für 3 bis 5 Sekunden Stille (am besten mit ganz leichtem Grundrauschen) aufgenommen 
   wird.
 
 
 