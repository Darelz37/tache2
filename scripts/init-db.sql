-- À exécuter UNE seule fois, avec le super-utilisateur PostgreSQL :
--   psql -U postgres -f scripts/init-db.sql
-- (ou coller le contenu dans l'outil Query de pgAdmin)
CREATE ROLE proclama WITH LOGIN PASSWORD 'proclama';
CREATE DATABASE proclama OWNER proclama;
