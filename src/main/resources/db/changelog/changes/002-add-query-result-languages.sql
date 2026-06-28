--liquibase formatted sql

--changeset vdobler:002-add-query-result-languages
--comment: Store the language variant of a provider offering (differentiates same-price listings).
alter table query_result add column languages varchar(255);
