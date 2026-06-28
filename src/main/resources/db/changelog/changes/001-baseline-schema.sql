--liquibase formatted sql

--changeset vdobler:001-baseline-schema
--comment: Baseline schema for the where-to-stream cache (query metadata + scraped results).
-- NOTE: this is a fresh baseline. The H2 database only holds cached scrape results, so for an
-- existing deployment remove the old ./db before the first Liquibase run; the cache repopulates
-- via /pre-cache. The DDL mirrors what Hibernate generates for the entities, so that
-- spring.jpa.hibernate.ddl-auto=validate succeeds.
create table query_meta (
    id uuid not null,
    imdb_id varchar(255),
    creation_time timestamp(6) with time zone,
    invalidated boolean,
    primary key (id)
);

create table query_result (
    id uuid not null,
    query_meta_id uuid,
    imdb_id varchar(255),
    title varchar(255),
    flatrate boolean,
    primary key (id)
);

create table query_result_availabilities (
    query_result_id uuid not null,
    type enum ('BUY','RENT'),
    sd varchar(255),
    hd varchar(255),
    fourk varchar(255)
);

alter table query_result
    add constraint fk_query_result_query_meta
    foreign key (query_meta_id) references query_meta;

alter table query_result_availabilities
    add constraint fk_availabilities_query_result
    foreign key (query_result_id) references query_result;
