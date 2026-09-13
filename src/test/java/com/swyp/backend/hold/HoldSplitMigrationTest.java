package com.swyp.backend.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V0027 splits a hold's items into holds of their own. Every other test starts from an empty
 * database, so the statements that move existing rows never run -- this one stops the migration
 * just before the split, puts a two-item hold in, and lets the split have it.
 */
class HoldSplitMigrationTest {

	private static final String BEFORE_THE_SPLIT = "25";
	private static final String THE_SPLIT = "27";

	private static PostgreSQLContainer postgres;

	@BeforeAll
	static void startDatabaseAndSplitAHold() throws SQLException {
		postgres = new PostgreSQLContainer("postgres:17");
		postgres.start();
		migrateTo(BEFORE_THE_SPLIT);
		seedATwoItemHold();
		migrateTo(THE_SPLIT);
	}

	@AfterAll
	static void stopDatabase() {
		postgres.stop();
	}

	@Test
	void eachItemBecomesAHoldOfItsOwn() throws SQLException {
		assertThat(scalar("select count(*) from holds")).isEqualTo(2);
		assertThat(column("select qty from holds order by product_id")).containsExactly("2", "3");
		assertThat(scalar("select count(*) from holds where product_id is null")).isZero();
	}

	@Test
	void theVisitStaysOneGroup() throws SQLException {
		assertThat(scalar("select count(distinct group_id) from holds")).isEqualTo(1);
	}

	@Test
	void everyColumnThatSaysWhereTheHoldStandsComesAcross() throws SQLException {
		assertThat(scalar("""
				select count(*) from holds
				where status = 'EXPIRED'
					and cancel_reason = '손이 모자랐어요'
					and no_show_charged_at is not null
					and expires_at is not null"""))
				.isEqualTo(2);
	}

	@Test
	void theReminderMarkComesAcross() throws SQLException {
		assertThat(scalar("select count(*) from holds where expiry_reminded_at is null"))
				.as("a row left null is picked up by the partial index and warned a second time")
				.isZero();
	}

	@Test
	void theOldItemTableIsGone() throws SQLException {
		assertThat(scalar("select count(*) from information_schema.tables where table_name = 'hold_items'"))
				.isZero();
	}

	private static void migrateTo(String version) {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.target(MigrationVersion.fromVersion(version))
				.load()
				.migrate();
	}

	private static void seedATwoItemHold() throws SQLException {
		execute("""
				insert into users (role, nickname, marketing_opt_in, terms_agreed_at, created_at, updated_at)
				values ('CONSUMER', '윤지현', false, now(), now(), now()),
					('OWNER', '청과마을사장', false, now(), now(), now());

				insert into stores (owner_user_id, name, address, phone, latitude, longitude,
					business_open_time, business_close_time, created_at, updated_at)
				values ((select id from users where role = 'OWNER'), '청과마을', '서울특별시 강남구 역삼로 1',
					'0212345678', 37.500000, 127.030000, '09:00', '21:00', now(), now());

				insert into products (store_id, name, category, initial_qty, available_qty, held_qty,
					original_price, sale_price, discount_rate, pickup_start_at, pickup_end_at,
					photo_url, created_at, updated_at)
				values ((select id from stores), '시금치 한 단', 'VEGETABLE', 10, 8, 2, 1000, 800, 20,
						now(), now() + interval '1 hour', 'https://example.com/a.jpg', now(), now()),
					((select id from stores), '애호박', 'VEGETABLE', 10, 7, 3, 1000, 800, 20,
						now(), now() + interval '1 hour', 'https://example.com/b.jpg', now(), now());

				insert into holds (user_id, store_id, status, expires_at, cancel_reason,
					no_show_charged_at, expiry_reminded_at, created_at, updated_at)
				values ((select id from users where role = 'CONSUMER'), (select id from stores),
					'EXPIRED', now() - interval '1 minute', '손이 모자랐어요', now(), now(), now(), now());

				insert into hold_items (hold_id, product_id, qty, created_at, updated_at)
				select (select id from holds), p.id,
					case when p.name = '시금치 한 단' then 2 else 3 end, now(), now()
				from products p;
				""");
	}

	private static void execute(String sql) throws SQLException {
		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static long scalar(String sql) throws SQLException {
		return Long.parseLong(column(sql).getFirst());
	}

	private static List<String> column(String sql) throws SQLException {
		try (Connection connection = connect();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			List<String> values = new ArrayList<>();
			while (resultSet.next()) {
				values.add(resultSet.getString(1));
			}
			return values;
		}
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}
}
