--
-- Consolidate number information from SPAMREPORTS, OLDREPORTS, RATINGS, RATINGHISTORY, SEARCHES, and SEARCHHISTORY 
-- into NUMBERS and NUMBERS_HISTORY
--

---------------------------------
-- REVISION
---------------------------------

CREATE TABLE REVISION (
	ID INTEGER NOT NULL AUTO_INCREMENT,
	CREATED BIGINT NOT NULL,
	CONSTRAINT REVISION_PK PRIMARY KEY (ID)
);

INSERT INTO REVISION (ID, CREATED) (
	SELECT ID, CREATED FROM SEARCHCLUSTER 
);

ALTER TABLE REVISION ALTER COLUMN ID INTEGER GENERATED ALWAYS AS IDENTITY;
ALTER TABLE REVISION ALTER COLUMN ID RESTART WITH (
	SELECT max(ID) + 1 FROM REVISION
);

---------------------------------
-- NUMBERS
---------------------------------

CREATE TABLE NUMBERS (
	PHONE CHARACTER VARYING(100) NOT NULL,
	ADDED BIGINT DEFAULT 0 NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	LASTSEARCH BIGINT DEFAULT 0 NOT NULL,
	LASTPING BIGINT DEFAULT 0 NOT NULL,
	LASTMETA BIGINT DEFAULT 0 NOT NULL,
	ACTIVE BOOLEAN DEFAULT TRUE NOT NULL,
	CALLS INTEGER DEFAULT 0 NOT NULL,
	VOTES INTEGER DEFAULT 0 NOT NULL,
	LEGITIMATE INTEGER DEFAULT 0 NOT NULL,
	PING INTEGER DEFAULT 0 NOT NULL,
	POLL INTEGER DEFAULT 0 NOT NULL,
	ADVERTISING INTEGER DEFAULT 0 NOT NULL,
	GAMBLE INTEGER DEFAULT 0 NOT NULL,
	FRAUD INTEGER DEFAULT 0 NOT NULL,
	SEARCHES INTEGER DEFAULT 0 NOT NULL,
	CONSTRAINT NUMBERS_PK PRIMARY KEY (PHONE)
);

CREATE INDEX NUMBERS_ACTIVE_IDX ON NUMBERS (ACTIVE DESC,VOTES DESC);
CREATE INDEX NUMBERS_UPDATED_IDX ON NUMBERS (UPDATED DESC);
CREATE INDEX NUMBERS_SEARCHES_IDX ON NUMBERS (SEARCHES DESC);

-- Clear new NUMBERS table after failed attempt
-----------------------------------------------
DELETE FROM NUMBERS ;

-- Insert votes from SPAMREPORTS and OLDREPORTS
-----------------------------------------------
INSERT INTO NUMBERS (PHONE, ADDED, UPDATED, VOTES, ACTIVE) (
	SELECT s.PHONE, s.DATEADDED, s.LASTUPDATE, s.VOTES, TRUE FROM SPAMREPORTS s
);

-- Insert old reports as active, too. The activation is computed later on with new rules.
INSERT INTO NUMBERS (PHONE, ADDED, UPDATED, VOTES, ACTIVE) (
	SELECT s.PHONE, s.DATEADDED, s.LASTUPDATE, s.VOTES, TRUE FROM OLDREPORTS s
	LEFT OUTER JOIN SPAMREPORTS x
	ON x.PHONE = s.PHONE
	WHERE x.PHONE IS NULL 
);

-- Statistics of active and inactive numbers
--------------------------------------------
SELECT COUNT(1), ACTIVE FROM NUMBERS GROUP BY ACTIVE; 

-- Move searches from SEARCHES to NUMBERS
---------------------------------------
UPDATE NUMBERS n 
SET SEARCHES = (SELECT s.COUNT FROM SEARCHES s WHERE n.PHONE = s.PHONE),
	LASTSEARCH = GREATEST(LASTSEARCH, (SELECT s.LASTUPDATE FROM SEARCHES s WHERE n.PHONE = s.PHONE)) 
WHERE EXISTS (SELECT s.COUNT FROM SEARCHES s WHERE n.PHONE = s.PHONE);

-- Move ratings from RATINGS to NUMBERS
---------------------------------------
UPDATE NUMBERS n 
SET PING = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='C_PING'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='C_PING')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='C_PING');

UPDATE NUMBERS n 
SET POLL = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='D_POLL'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='D_POLL')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='D_POLL');

UPDATE NUMBERS n 
SET ADVERTISING = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='E_ADVERTISING'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='E_ADVERTISING')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='E_ADVERTISING');

UPDATE NUMBERS n 
SET GAMBLE = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='F_GAMBLE'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='F_GAMBLE')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='F_GAMBLE');

UPDATE NUMBERS n 
SET FRAUD = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='G_FRAUD'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='G_FRAUD')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='G_FRAUD');

UPDATE NUMBERS n 
SET LEGITIMATE = (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='A_LEGITIMATE'),
	UPDATED = GREATEST(UPDATED , (SELECT s.LASTUPDATE FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='A_LEGITIMATE')) 
WHERE EXISTS (SELECT s.COUNT FROM RATINGS s WHERE n.PHONE = s.PHONE AND s.RATING='A_LEGITIMATE');

---------------------------------
-- NUMBERS_AGGREGATION_10
---------------------------------

CREATE TABLE NUMBERS_AGGREGATION_10 (
	PREFIX CHARACTER VARYING(100) NOT NULL,
	CNT INTEGER NOT NULL,
	VOTES INTEGER NOT NULL,
	CONSTRAINT NUMBERS_AGGREGATION_10_PK PRIMARY KEY (PREFIX)
);

-- Update aggregation tables
----------------------------
DELETE FROM NUMBERS_AGGREGATION_10 ;

INSERT INTO NUMBERS_AGGREGATION_10 (PREFIX, CNT, VOTES) (
	SELECT a.prefix prefix, COUNT(1) cnt, SUM(a.votes) votes FROM (
		SELECT SUBSTRING(s.PHONE, 0, LENGTH(s.phone) - 1) prefix, s.VOTES votes 
		FROM NUMBERS s
	) a
	GROUP BY a.prefix
);

---------------------------------
-- NUMBERS_AGGREGATION_100
---------------------------------

CREATE TABLE NUMBERS_AGGREGATION_100 (
	PREFIX CHARACTER VARYING(100) NOT NULL,
	CNT INTEGER NOT NULL,
	VOTES INTEGER NOT NULL,
	CONSTRAINT NUMBERS_AGGREGATION_100_PK PRIMARY KEY (PREFIX)
);

DELETE FROM NUMBERS_AGGREGATION_100 ;

INSERT INTO NUMBERS_AGGREGATION_100 (PREFIX, CNT, VOTES) (
	SELECT a.prefix prefix, COUNT(1) cnt, SUM(a.votes) votes FROM (
		SELECT SUBSTRING(s.PREFIX, 0, LENGTH(s.PREFIX) - 1) prefix, s.VOTES votes 
		FROM NUMBERS_AGGREGATION_10 s
		WHERE s.CNT >= 4
	) a
	GROUP BY a.prefix
);

---------------------------------
-- NUMBERS_HISTORY
---------------------------------

DROP TABLE NUMBERS_HISTORY IF EXISTS;

CREATE TABLE NUMBERS_HISTORY (
	RMIN INTEGER NOT NULL,
	RMAX INTEGER NOT NULL,
	PHONE CHARACTER VARYING(100) NOT NULL,
	ACTIVE BOOLEAN DEFAULT TRUE NOT NULL,
	CALLS INTEGER DEFAULT 0 NOT NULL,
	VOTES INTEGER DEFAULT 0 NOT NULL,
	LEGITIMATE INTEGER DEFAULT 0 NOT NULL,
	PING INTEGER DEFAULT 0 NOT NULL,
	POLL INTEGER DEFAULT 0 NOT NULL,
	ADVERTISING INTEGER DEFAULT 0 NOT NULL,
	GAMBLE INTEGER DEFAULT 0 NOT NULL,
	FRAUD INTEGER DEFAULT 0 NOT NULL,
	SEARCHES INTEGER DEFAULT 0 NOT NULL,
	CONSTRAINT NUMBERS_HISTORY_PK PRIMARY KEY (RMAX,PHONE)
);

-- Copy SEARCHHISTORY
INSERT INTO NUMBERS_HISTORY (RMIN, RMAX, PHONE, SEARCHES) (
	SELECT s.CLUSTER, s.CLUSTER, s.PHONE, s.COUNT FROM SEARCHHISTORY s
);

-- Create missing entries required to copy RATINGHISTORY contents
INSERT INTO NUMBERS_HISTORY (RMIN, RMAX, PHONE) (
	SELECT DISTINCT s.REV, s.REV, s.PHONE FROM RATINGHISTORY s
	LEFT OUTER JOIN NUMBERS_HISTORY h ON h.RMAX = s.REV AND h.PHONE = s.PHONE
	WHERE h.PHONE IS NULL 
);

-- Move rating history to NUMBERS_HISTORY
UPDATE NUMBERS_HISTORY n 
SET LEGITIMATE = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'A_LEGITIMATE'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'A_LEGITIMATE'
);

UPDATE NUMBERS_HISTORY n 
SET PING = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'C_PING'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'C_PING'
);

UPDATE NUMBERS_HISTORY n 
SET POLL = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'D_POLL'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'D_POLL'
);

UPDATE NUMBERS_HISTORY n 
SET ADVERTISING = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'E_ADVERTISING'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'E_ADVERTISING'
);

UPDATE NUMBERS_HISTORY n 
SET GAMBLE = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'F_GAMBLE'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'F_GAMBLE'
);

UPDATE NUMBERS_HISTORY n 
SET FRAUD = (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'G_FRAUD'
)	 
WHERE EXISTS (
	SELECT s.COUNT FROM RATINGHISTORY s WHERE s.REV = n.RMAX AND s.PHONE = n.PHONE AND s.RATING = 'G_FRAUD'
);


-- Import META_UPDATE
UPDATE NUMBERS n SET LASTMETA = COALESCE( (SELECT u.LASTUPDATE FROM META_UPDATE u WHERE u.PHONE = n.PHONE), 0);

-- Compute initial value for LASTPING
UPDATE NUMBERS n
SET n.LASTPING = GREATEST(n.LASTSEARCH, n.UPDATED); 

-- Revision ranges are completed in code.
-- History information diff is expanded in code.