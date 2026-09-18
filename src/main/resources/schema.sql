-- StapleTrack NG schema. Runs on every startup (spring.sql.init.mode=always), so every
-- statement must be idempotent. Hibernate validates the entities against these tables.
-- month_year holds YearMonth as "YYYY-MM" (see YearMonthConverter).

CREATE TABLE IF NOT EXISTS national_price (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    item       VARCHAR(120)   NOT NULL,
    month_year VARCHAR(7)     NOT NULL,
    price      DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_national_item_month UNIQUE (item, month_year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS zonal_price (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    item       VARCHAR(120)   NOT NULL,
    month_year VARCHAR(7)     NOT NULL,
    zone       VARCHAR(20)    NOT NULL,
    price      DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_zonal_item_month_zone UNIQUE (item, month_year, zone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS state_extreme (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    item       VARCHAR(120)   NOT NULL,
    month_year VARCHAR(7)     NOT NULL,
    kind       VARCHAR(10)    NOT NULL,
    state      VARCHAR(40)    NOT NULL,
    price      DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_extreme_item_month_kind UNIQUE (item, month_year, kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
