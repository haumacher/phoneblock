/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import de.haumacher.phoneblock.app.api.model.Rating;

/**
 * Mapper for the partner report exchange (see
 * <code>docs/plans/2026-11-23-partner-report-exchange-design.md</code>).
 *
 * <p>
 * The export reads the personal block-list entries of PhoneBlock users ({@code PERSONALIZATION}),
 * the import records the counted partner reports in the ledger {@code EXTERNAL_REPORTS}.
 * </p>
 */
public interface ExternalReports {

	/**
	 * A personal block-list entry of a PhoneBlock user to export, enriched with the user's rating
	 * and comment for that number (both <code>null</code>, if the user has no {@code COMMENTS} row
	 * for it).
	 */
	record ExportRow(long userId, String phone, long created, Rating rating, String comment) {}

	/**
	 * The exact (non-wildcard) personal block-list entries created in <code>[from, to)</code>,
	 * ordered by creation time.
	 *
	 * <p>
	 * Only entries for numbers with at least <code>minVotes</code> community votes
	 * ({@code NUMBERS.VOTES}, all sources) at query time are selected (corroboration filter).
	 * </p>
	 *
	 * <p>
	 * Each entry is joined with at most one {@code COMMENTS} row of the same user for the same
	 * number (the newest one), so that the result has exactly one row per {@code PERSONALIZATION}
	 * row, even if duplicate comment rows exist. Only rows with {@code USERID} set qualify, comments
	 * imported by a meta search or a partner import never do.
	 * </p>
	 */
	@Select("""
			select p.USERID, p.PHONE, p.CREATED, c.RATING, c.COMMENT
			from PERSONALIZATION p
			inner join NUMBERS n on n.PHONE = p.PHONE and n.VOTES >= #{minVotes}
			left outer join COMMENTS c on c.ID = (
				select c2.ID from COMMENTS c2
				where c2.USERID = p.USERID and c2.PHONE = p.PHONE
				order by c2.CREATED desc, c2.ID desc
				limit 1
			)
			where p.BLOCKED = true
			and p.WILDCARD = false
			and p.CREATED >= #{from}
			and p.CREATED < #{to}
			order by p.CREATED, p.USERID, p.PHONE
			""")
	List<ExportRow> getBlockedEntries(long from, long to, int minVotes);

	/**
	 * Whether the report with the given ID of the given source has already been imported.
	 */
	@Select("select count(1) > 0 from EXTERNAL_REPORTS where SOURCE = #{source} and ID = #{id}")
	boolean hasReport(String source, String id);

	/**
	 * Records an imported partner report.
	 */
	@Insert("""
			insert into EXTERNAL_REPORTS (SOURCE, ID, PHONE, SENDER, CREATED, RATING)
			values (#{source}, #{id}, #{phone}, #{sender}, #{created}, #{rating})
			""")
	void addReport(String source, String id, String phone, String sender, long created, Rating rating);

	/**
	 * Number of reports imported from the given source.
	 */
	@Select("select count(1) from EXTERNAL_REPORTS where SOURCE = #{source}")
	int countReports(String source);

}
