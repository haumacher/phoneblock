-- FTC import (#657): the import now fetches complaints hour by hour from api.ftc.gov and tracks its
-- position in 'ftc.sync.next_hour'. The old day position was advanced although www.ftc.gov answered
-- every download with HTTP 404, so it is dropped to re-import the bootstrap period.
DELETE FROM PROPERTIES WHERE NAME = 'ftc.sync.last_date';
