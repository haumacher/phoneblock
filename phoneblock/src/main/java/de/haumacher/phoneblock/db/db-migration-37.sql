-- #300 privacy guard: the SHA1 column is a reverse-lookup (rainbow) table, so it
-- must only exist for spam-visible numbers. Searches and meta-search seeds used to
-- store the hash for legitimate, never-voted numbers; clear it for every number
-- whose classification is not above legitimate (SPAM_EVIDENCE <= LEGIT_EVIDENCE) —
-- the same cut the hash-prefix lookup applies. Spam numbers keep their hash.
UPDATE NUMBERS SET SHA1 = NULL WHERE SPAM_EVIDENCE <= LEGIT_EVIDENCE;
