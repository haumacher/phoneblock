# Templates for the PhoneBlock web site

This folder contains sub-directories for each supported language. Each language directory contains 
the templates that are displayed, if a page in that language is requested. 

The templates for language `de` are the originals, since German is the native language, for which
PhoneBlock was developed. Templates for all other languages are synthesized by an automatic process
by extracting properties with text contents from the German variant, automatic translation to all 
other supported languages and then synthesizing language-specific templates from the translated properties
and the original German templates. 

The tooling for automatic translation of templates and properties can be found here:
https://github.com/haumacher/auto-translate

To trigger the translation, call the following target:

```
mvn 