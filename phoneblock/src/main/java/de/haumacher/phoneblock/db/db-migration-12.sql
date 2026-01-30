-- Clear SHA1 hashes for numbers with votes below 1 to protect privacy.
-- Legitimate numbers should not be identifiable through privacy-aware lookups.
UPDATE NUMBERS SET SHA1 = NULL WHERE VOTES < 1;
