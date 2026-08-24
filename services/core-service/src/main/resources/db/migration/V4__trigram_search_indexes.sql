CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_medications_canonical_name_trgm
    ON medications USING GIN (lower(canonical_name) gin_trgm_ops);
CREATE INDEX idx_medications_generic_name_trgm
    ON medications USING GIN (lower(generic_name) gin_trgm_ops);
CREATE INDEX idx_manufacturers_source_name_trgm
    ON manufacturers USING GIN (lower(source_name) gin_trgm_ops);
